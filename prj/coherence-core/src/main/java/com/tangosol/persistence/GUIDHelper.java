/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package com.tangosol.persistence;

import com.oracle.coherence.common.base.Logger;

import com.oracle.coherence.persistence.PersistentStoreInfo;

import com.tangosol.net.Member;
import com.tangosol.net.partition.PartitionSet;

import com.tangosol.util.Base;
import com.tangosol.util.ConverterCollections;
import com.tangosol.util.ImmutableMultiList;
import com.tangosol.util.NullImplementation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static com.tangosol.util.Base.out;

/**
 * Static helper methods to encode and decode the attributes related to the
 * storage of a persistent form of a partition.
 * <p>
 * A persistence GUID must at minimum reflect the partition-id and a monotonically
 * increasing partition-version that could be used to determine, given a set
 * of GUIDs representing the same partition, a total ordering of the GUIDs over
 * time.
 *
 * @author rhl/jh 2012.07.06
 */
public class GUIDHelper
    {
    // ----- static methods -------------------------------------------------

    /**
     * Generate and return a new GUID for the specified partition.
     *
     * @param nPartition  the partition to return a GUID for
     * @param lVersion    the creation version of the partition
     * @param ldt         the creation timestamp; informational only
     * @param member      the member generating the GUID; informational only
     *
     * @return a new GUID for the specified partition
     */
    public static String generateGUID(int nPartition, long lVersion, long ldt, Member member)
        {
        return String.format("%d-%x-%x-%d", nPartition, lVersion, ldt, member.getId());
        }

    /**
     * Validate the given GUID.
     *
     * @param sGUID  the GUID to validate
     *
     * @return true if the specified GUID is valid; false otherwise
     */
    public static boolean validateGUID(String sGUID)
        {
        return sGUID != null && sGUID.matches("\\d+-[0-9a-f]+-[0-9a-f]+-\\d+");
        }

    /**
     * Parse the specified GUID and return the partition-id.
     *
     * @param sGUID  the GUID to return the partition-id for
     *
     * @return the partition-id
     */
    public static int getPartition(String sGUID)
        {
        return Integer.parseInt(parseAttribute(sGUID, 0));
        }

    /**
     * Parse the specified GUID and return the partition-version.
     *
     * @param sGUID  the GUID to return the partition-version for
     *
     * @return the partition-version
     */
    public static long getVersion(String sGUID)
        {
        return Long.parseLong(parseAttribute(sGUID, 1), 16);
        }

    /**
     * Parse the specified GUID and return the service join time.
     *
     * @param sGUID  the GUID used to return the service join time
     *
     * @return the service join
     */
    public static long getServiceJoinTime(String sGUID)
        {
        return Long.parseLong(parseAttribute(sGUID, 2), 16);
        }

    /**
     * Parse the specified GUID and return the originating member-id.
     *
     * @param sGUID  the GUID to return the originating member-id for
     *
     * @return the originating member-id
     */
    public static int getMemberId(String sGUID)
        {
        return Integer.parseInt(parseAttribute(sGUID, 3));
        }

    /**
     * Return GUID in the specified list of persistent store info for the
     * specified partition.
     *
     * @param listInfo    the list of persistent store info objects
     * @param nPartition  the partition
     *
     * @return GUID for the specified partition
     */
    public static String getGUID(List<PersistentStoreInfo> listInfo, int nPartition)
        {
        for (PersistentStoreInfo p : listInfo)
            {
            String s = p.getId();
            if (getPartition(s) == nPartition)
                {
                return s;
                }
            }
        return null;
        }

    /**
     * Return a Map containing assignments of member id to stores based on the
     * given constraints.
     * <p>
     * The algorithm will attempt to fairly distribute assignment of stores across
     * members while abiding to the given constraints.
     * For example:
     * <table>
     *   <caption>Member constraints and assignments</caption>
     *   <tr><th>Member Id</th><th>Constraints</th><th>Assignments</th></tr>
     *   <tr><td>1</td><td>{a,b,c,d}</td><td>{a,c}</td></tr>
     *   <tr><td>2</td><td>{a,b,c,d}</td><td>{b,d}</td></tr>
     *   <tr><td>3</td><td>{e,f,g,h}</td><td>{e,g}</td></tr>
     *   <tr><td>4</td><td>{e,f,g,h}</td><td>{f,h}</td></tr>
     * </table>
     *
     * @param mapConstraints   the constraints to perform assignments within
     * @param cDistinctStores  the number of expected distinct stores
     *
     * @return a Map containing assignments of member id to stores based on the
     *         given constraints
     */
    public static Map<Integer, String[]> assignStores(Map<Integer, Object[]> mapConstraints, int cDistinctStores)
        {
        if (mapConstraints == null || mapConstraints.size() == 0)
            {
            throw new IllegalArgumentException("Unexpected empty map of member to persistent stores");
            }

        // Internal Notes:
        // this algorithm implements a trivial round-robin store assignment
        // strategy. To achieve this the provided (member-id -> stores)
        // data structure is inverted to (stores -> member-ids) thus creating
        // sets of members with the same store visibility; each set of members
        // is assigned stores from the common pool of stores.
        // Assumptions/Comments:
        //   - assumes a clean slate, a.k.a there are no current assignments
        //   - in the common (and realistic) case there are non-intersecting
        //     sets of members; it is unlikely an intersect exists between two
        //     sets of members such that members have some shared and some
        //     not-shared stores; the algorithm does accommodate for this scenario
        //     but does *not* expend effort in achieving even distribution

        Map<List<String>, Integer[]> mapGUIDMembers = new HashMap<>();

        Set<String> setSharedGUIDs   = new HashSet<>();
        Set<String> setAssignedGUIDs = new HashSet<>();

        Map.Entry<Integer, Object[]>[] aEntry = createSortedEntries(mapConstraints);

        for (int i = 0, c = aEntry.length; i < c; ++i)
            {
            Integer      NMemberCurrent = aEntry[i].getKey();
            Object[]     aoGUIDs        = aEntry[i].getValue();
            List<String> listGUIDs      = (List) Arrays.asList(aoGUIDs);

            if (mapGUIDMembers.containsKey(listGUIDs))
                {
                continue;
                }

            List<Integer> listMembers = new ArrayList<>();

            listMembers.add(NMemberCurrent);

            for (int j = i + 1; j < c; ++j)
                {
                Object[] aoGUIDThat = aEntry[j].getValue();

                List<String> listIntersection = intersects(aoGUIDs, aoGUIDThat);

                int cIntersection = listIntersection == null ? 0 : listIntersection.size();
                if (cIntersection == aoGUIDs.length &&
                    cIntersection == aoGUIDThat.length) // equal
                    {
                    listMembers.add(aEntry[j].getKey());
                    if (j == i + 1)
                        {
                        ++i;
                        }
                    }
                else if (cIntersection > 0) // !equal but some shared GUIDs
                    {
                    setSharedGUIDs.addAll(listIntersection);
                    }
                }
            mapGUIDMembers.put(listGUIDs, listMembers.toArray(new Integer[listMembers.size()]));
            }

        // sort in a such a way the least 'visible' GUIDs are assigned first
        //noinspection unchecked
        List<String>[] alistGUIDs = mapGUIDMembers.keySet().toArray(new List[mapGUIDMembers.size()]);
        Arrays.sort(alistGUIDs, (listThis, listThat) -> listThis.size() - listThat.size());

        Map<Integer, List<String>> mapMemberGUID = new HashMap<>();
        for (List<String> listGUIDs : alistGUIDs)
            {
            Integer[] aNMembers = mapGUIDMembers.get(listGUIDs);

            for (int i = 0, c = listGUIDs.size(), cMembers = aNMembers.length; i < c; ++i)
                {
                String sGUID = listGUIDs.get(i);

                if (!setSharedGUIDs.contains(sGUID) || !setAssignedGUIDs.contains(sGUID))
                    {
                    Integer NMember = aNMembers[i % cMembers];
                    mapMemberGUID.computeIfAbsent(NMember, key -> new ArrayList<>());

                    mapMemberGUID.get(NMember).add(sGUID);

                    boolean fAdded = setAssignedGUIDs.add(sGUID);
                    assert fAdded : sGUID + " was assigned twice; 2nd assignment to member " + NMember;
                    }
                }
            }

        if (setAssignedGUIDs.size() != cDistinctStores) // error checking
            {
            // two scenarios exist in which we can enter this error state:
            //   1. invalid argument - the provided number of distinct stores
            //                         was not as expected
            //   2. algorithm error  - the algorithm illegally did not assign a
            //                         provided store; add some debug info to the log

            Set<Object> setDistinctStores = new HashSet<>(setAssignedGUIDs.size());
            String      sMsg              = "Unexpected number of distinct stores; expected " +
                    cDistinctStores + " but found " + setAssignedGUIDs.size();

            StringBuilder sb = new StringBuilder("[");
            mapConstraints.forEach((NMemberId, aoStore) ->
                {
                sb.append(NMemberId).append("->").append(Arrays.toString(aoStore));
                setDistinctStores.addAll(Arrays.asList(aoStore));
                });
            sb.append(']');

            if (setDistinctStores.size() != setAssignedGUIDs.size())
                {
                Logger.err("Unexpected number of distinct stores\nassigned: " + setAssignedGUIDs +
                        "\npassed: " + sb.toString());
                throw new IllegalStateException(sMsg);
                }

            throw new IllegalArgumentException(sMsg);
            }

        // Map<Integer, List<String>> -> Map<Integer, String[]>
        return ConverterCollections.getMap(mapMemberGUID,
                NullImplementation.getConverter(),
                NullImplementation.getConverter(),
                listGUIDs -> listGUIDs == null ? null : listGUIDs.toArray(new String[listGUIDs.size()]), // List<String> -> String[]
                NullImplementation.getConverter());
        }

    /**
     * Return a {@link PartitionSet} with all partitions present in the provided
     * {@code mapStores}.
     *
     * @param mapStores  a Map of member id to stores
     * @param cParts     the partition count
     *
     * @return a PartitionSet with all partitions present in the provided mapStores
     */
    public static PartitionSet getPartitions(Map<Integer, Object[]> mapStores, int cParts)
        {
        PartitionSet parts = new PartitionSet(cParts);
        for (Object[] aoStore : mapStores.values())
            {
            for (Object oStore : aoStore)
                {
                PersistentStoreInfo info = (PersistentStoreInfo) oStore;
                parts.add(getPartition(info.getId()));
                }
            }
        return parts;
        }

    // ----- helpers --------------------------------------------------------

    /**
     * Return the specified (0-based) attribute from the GUID.
     *
     * @param sGUID  the GUID
     * @param id     the (0-based) attribute index
     *
     * @return the GUID attribute
     */
    protected static String parseAttribute(String sGUID, int id)
        {
        StringTokenizer tokenizer = new StringTokenizer(sGUID, "-");

        for (int i = 0; i < id; i++)
            {
            tokenizer.nextToken();
            }

        return tokenizer.nextToken();
        }

    /**
     * Return a List of stores (GUIDs) that allows the caller to derive whether
     * the provided sets are disjoint, intersect or equal. These three states
     * can be derived as follows:
     * <table>
     *     <caption>States</caption>
     *     <tr><th>Result</th><th>Reason</th></tr>
     *     <tr><td>null</td><td>disjoint sets</td></tr>
     *     <tr><td>listReturn</td><td>shared elements across LHS and RHS</td></tr>
     *     <tr><td>listReturn.size() == aoLHS.length == aoRHS.length</td><td>LHS and RHS are equal</td></tr>
     * </table>
     *
     * @param aoLHS  the first set in the comparison
     * @param aoRHS  the second set in the comparison
     *
     * @return a List of stores that allows the caller to derive whether the
      *        provided sets are disjoint, intersect or equal
     */
    protected static List<String> intersects(Object[] aoLHS, Object[] aoRHS)
        {
        assert aoLHS != null && aoRHS != null;

        // quick disjoint check based on both LHS & RHS being sorted:
        //     tail(LHS) < head(RHS) || tail(RHS) < head(LHS)
        if (((String) aoLHS[aoLHS.length - 1]).compareTo((String) aoRHS[0]) < 0 ||
            ((String) aoRHS[aoRHS.length - 1]).compareTo((String) aoLHS[0]) < 0)
            {
            return null;
            }

        // the two arrays of stores do overlap; derive the intersect

        List<String> listIntersect = null;
        int          nCompare      = aoLHS.length - aoRHS.length;
        Object[]     aoLarger      = nCompare == 0 || nCompare < 0 ? aoRHS : aoLHS;
        Object[]     aoSmaller     = nCompare == 0 || nCompare < 0 ? aoLHS : aoRHS;

        for (int i = 0, j = 0, cMax = aoLarger.length, cMin = aoSmaller.length; i < cMax; )
            {
            nCompare = j < cMin ? ((Comparable) aoLarger[i]).compareTo(aoSmaller[j]) : -1;

            if (nCompare == 0)
                {
                if (listIntersect != null)
                    {
                    listIntersect.add((String) aoLarger[i]);
                    }
                }
            else if (listIntersect == null)
                {
                listIntersect = new ArrayList<>();
                if (i > 0)
                    {
                    //noinspection unchecked;go raw - must be string elements
                    listIntersect.addAll((List) Arrays.asList(Arrays.copyOfRange(aoLarger, 0, i)));
                    }
                }

            if (nCompare <= 0)
                {
                ++i;
                }
            if (nCompare >= 0)
                {
                ++j;
                }
            }

        return listIntersect == null
                    ? (List) Arrays.asList(aoLHS) : // LHS == RHS
               listIntersect.isEmpty()
                    ? null                   // disjoint sets
                    : listIntersect;         // intersect of the two sets
        }

    /**
     * Return an array of {@link Map.Entry entries} based on the provided map
     * ensuring the value ({@code Object} array) is sorted.
     *
     * @param map  a map to base the Entry[] on

     * @param <K> - the key type

     * @return an array of {@link Map.Entry entries} ensuring the value
     *         ({@code Object} array) is sorted
     */
    protected static <K> Map.Entry<K, Object[]>[] createSortedEntries(Map<K, Object[]> map)
        {
        Map.Entry[] aEntry = new Map.Entry[map.size()];

        int i      = 0;
        int cArray = aEntry.length;
        for (Map.Entry<K,Object[]> entry : map.entrySet())
            {
            Arrays.sort(entry.getValue());

            if (i >= cArray)
                {
                Map.Entry[] aOld   = aEntry;
                            aEntry = new Map.Entry[cArray + (cArray >> 3)];

                System.arraycopy(aOld, 0, aEntry, 0, cArray);
                cArray = aEntry.length;
                }
            aEntry[i++] = entry;
            }

        if (i < cArray)
            {
            Map.Entry[] aOld   = aEntry;
                        aEntry = new Map.Entry[i];

            System.arraycopy(aOld, 0, aEntry, 0, i);
            }

        //noinspection unchecked; defer necessary uncheck assignment
        return aEntry;
        }

    /**
     * Convert a map with values of PersistentInfo array to values of String array
     *
     * @param mapStoreInfo  the map to be converted
     *
     * @return the converted map
     *
     * @since 24.09
     */
    public static Map<Integer, String[]> getMapGuids(Map<Integer, Object[]> mapStoreInfo)
        {
        Map<Integer, String[]> mapGUID = new HashMap();
        List<String>          listGUID = new ArrayList<>();
        for (Map.Entry entry : mapStoreInfo.entrySet())
            {
            // POF widens to Object[], need to cast individual elements
            Object[] aOInfo = (Object[]) entry.getValue();
            for (Object oInfo : aOInfo)
                {
                listGUID.add(((PersistentStoreInfo) oInfo).getId());
                }
            mapGUID.put((Integer) entry.getKey(), listGUID.toArray(new String[listGUID.size()]));
            listGUID.clear();
            }

        return mapGUID;
        }

    // ----- inner class: GUIDResolver --------------------------------------

    /**
     * Resolver used during a recovery to discover the newest available GUID
     * for a given partition.
     */
    public static class GUIDResolver
            extends Base
        {
        // ----- constructors -----------------------------------------------

        /**
         * Construct a GUIDResolver for the specified partition-count.
         *
         * @param cPartitions  the partition-count
         */
        public GUIDResolver(int cPartitions)
            {
            m_cPartitions  = cPartitions;
            f_mapStoreInfo = new HashMap<>();
            }

        // ----- GUIDResolver methods ---------------------------------------

        /**
         * Register the specified list of GUIDs from the specified member.
         *
         * @param member  the member
         * @param aInfo   the list of PersistentStoreInfo
         */
        public void registerStoreInfo(Member member, PersistentStoreInfo[] aInfo)
            {
            f_mapStoreInfo.put(member, aInfo);

            m_mapResolved = null; // reset the resolved map
            }

        /**
         * Return the newest GUID for the specified partition.
         *
         * @param nPartition  the partition to return a GUID for
         *
         * @return the newest GUIDs
         */
        public String getNewestGUID(int nPartition)
            {
            resolve();

            return m_aStoreNewest[nPartition].getId();
            }

        /**
         * Return the list of the newest GUIDs for the specified set of partitions.
         *
         * @param parts     the set of partitions to return GUIDs for
         *
         * @return the list of newest GUIDs
         */
        public String[] getNewestGUIDs(PartitionSet parts)
            {
            // ensure that the GUIDs have been resolved
            resolve();

            int                   cPartitions  = parts.cardinality();
            PersistentStoreInfo[] aStoreNewest = m_aStoreNewest;
            String[]              asGUIDResult = new String[cPartitions];
            int                   cResults     = 0;
            for (int iPart = parts.next(0); iPart >= 0; iPart = parts.next(iPart + 1))
                {
                PersistentStoreInfo info = aStoreNewest[iPart];
                if (info != null)
                    {
                    asGUIDResult[cResults++] = info.getId();
                    }
                }

            if (cResults < cPartitions)
                {
                // shrink the GUID list
                String[] asNew = new String[cResults];
                System.arraycopy(asGUIDResult, 0, asNew, 0, cResults);
                asGUIDResult = asNew;
                }

            return asGUIDResult;
            }

        /**
         * Return the list of the PersistentStoreInfo with newest GUIDs
         * for the specified set of partitions.
         *
         * @param parts  the set of partitions to return PersistentStoreInfo for
         *
         * @return the list of the PersistentStoreInfo with newest GUIDs
         *
         * @since 24.09
         */
        public PersistentStoreInfo[] getNewestStoreInfos(PartitionSet parts)
            {
            // ensure that the GUIDs have been resolved
            resolve();

            int                   cPartitions   = parts.cardinality();
            PersistentStoreInfo[] asStoreNewest = m_aStoreNewest;
            PersistentStoreInfo[] asStoreResult = new PersistentStoreInfo[cPartitions];
            int                   cResults      = 0;
            for (int iPart = parts.next(0); iPart >= 0; iPart = parts.next(iPart + 1))
                {
                PersistentStoreInfo info = asStoreNewest[iPart];
                if (info != null)
                    {
                    asStoreResult[cResults++] = info;
                    }
                }

            if (cResults < cPartitions)
                {
                // shrink the GUID list
                PersistentStoreInfo[] aNew = new PersistentStoreInfo[cResults];
                System.arraycopy(asStoreResult, 0, aNew, 0, cResults);
                asStoreResult = aNew;
                }

            return asStoreResult;
            }

        /**
         * Retrieve a PartitionSet containing all partitions that don't have any
         * corresponding GUIDs.
         *
         * @return a PartitionSet of all unresolved partitions
         */
        public PartitionSet getUnresolvedPartitions()
            {
            resolve();

            return m_partsUnresolved;
            }

        /**
         * Retrieve a map of member to a list of GUIDs to be deleted.
         *
         * @return a map of member to a list of GUIDs to be deleted
         */
        public Map<Member, List<String>> getInvalidGUIDs()
            {
            resolve();

            return m_mapCleanup;
            }

        /**
         * Return a Map of member id to an array of PersistentStoreInfo.
         *
         * @return a Map of member id to an array of PersistentStoreInfo
         */
        public Map<Integer, PersistentStoreInfo[]> getMemberStoreInfo()
            {
            return ConverterCollections.getMap(f_mapStoreInfo,
                    Member::getId,
                    NullImplementation.getConverter(),
                    NullImplementation.getConverter(),
                    NullImplementation.getConverter());
            }

        /**
         * Check whether ior not all the partition stores visible by every member.
         *
         * @return true iff all the partition stores visible by every member
         */
        public boolean isSharedStorage()
            {
            resolve();

            return m_fSharedStorage;
            }

        /**
         * Resolve the registered PersistentStoreInfos and return a map associating each
         * member to the set of partitions that it had registered as having the newest
         * GUID for, except when the newest store is empty while the older
         * version is not.
         *
         * @return a map associating each member to the set of partitions it has
         *         the latest GUID for
         */
        public Map<Member, PartitionSet> resolve()
            {
            Map<Member, PartitionSet> mapResolved = m_mapResolved;
            if (mapResolved != null)
                {
                // already resolved;
                return mapResolved;
                }

            int                     cPartitions   = m_cPartitions;
            PartitionSet            partsResolved = new PartitionSet(cPartitions);
            List<String>            listDelete    = new ArrayList<>();
            Set<String>             setPrevGUIDs  = new HashSet<>();
            PersistentStoreInfo[]   aStoreNewest  = new PersistentStoreInfo[cPartitions];
            Map<Member, PartitionSet> mapOwnershipRecover = new HashMap<>();

            Collection<PersistentStoreInfo> colStoreInfo = new ImmutableMultiList(f_mapStoreInfo.values());
            for (PersistentStoreInfo info : colStoreInfo)
                {
                String  sGUID      = info.getId();
                int     iPartition = getPartition(sGUID);
                if (iPartition >= cPartitions)
                    {
                    // there may be legally named GUIDs that are found, from a
                    // previous service incarnation with a different partition-count
                    setPrevGUIDs.add(sGUID);
                    continue;
                    }

                PersistentStoreInfo infoLatest = aStoreNewest[iPartition];

                if (infoLatest == null)
                    {
                    infoLatest = info;
                    }
                else if (infoLatest.isEmpty() ^ info.isEmpty())
                    {
                    listDelete.add((info.isEmpty() ? info : infoLatest).getId());
                    infoLatest = info.isEmpty() ? infoLatest : info;
                    }
                else if (getVersion(info.getId()) > getVersion(infoLatest.getId()))
                    {
                    setPrevGUIDs.add(infoLatest.getId());
                    infoLatest = info;
                    }
                else if (getVersion(info.getId()) < getVersion(infoLatest.getId()))
                    {
                    setPrevGUIDs.add(info.getId());
                    }

                aStoreNewest[iPartition]  = infoLatest;
                }

            for (Map.Entry<Member, PersistentStoreInfo[]> entry : f_mapStoreInfo.entrySet())
                {
                Member                member     = entry.getKey();
                PersistentStoreInfo[] aStoreInfo = entry.getValue();
                PartitionSet          parts      = new PartitionSet(cPartitions);
                for (int i = 0, c = aStoreInfo.length; i < c; i++)
                    {
                    PersistentStoreInfo storeInfo = aStoreInfo[i];
                    if (!setPrevGUIDs.contains(storeInfo.getId()))
                        {
                        parts.add(getPartition(storeInfo.getId()));
                        }
                    }

                mapOwnershipRecover.put(member, parts);
                partsResolved.add(parts);
                }

            // walk the map of members and their registered GUID lists to find,
            // for each member, the list of empty GUIDs that the member
            // should be responsible to clean up
            Map<Member,  List<String>> mapOwnershipCleanup = new HashMap<>();
            for (String sGUID : listDelete)
                {
                for (Map.Entry<Member, PersistentStoreInfo[]> entry : f_mapStoreInfo.entrySet())
                    {
                    PersistentStoreInfo[] aInfoThis = entry.getValue();

                    for (int i = 0, c = aInfoThis.length; i < c; i++)
                        {
                        if (equals(sGUID, aInfoThis[i].getId()))
                            {
                            Member member = entry.getKey();
                            List<String> list = mapOwnershipCleanup.get(member);
                            if (list == null)
                                {
                                list = new ArrayList();
                                mapOwnershipCleanup.put(member, list);
                                }

                            list.add(sGUID);
                            break;
                            }
                        }
                    }
                }

            // check if the storage is "shared" - meaning that all the members
            // can see all the partition stores
            boolean fSharedStorage = true;
            for (PartitionSet partsMember : mapOwnershipRecover.values())
                {
                if (!partsMember.equals(partsResolved))
                    {
                    fSharedStorage = false;
                    break;
                    }
                }

            // store the resolved results
            m_mapResolved     = mapOwnershipRecover;
            m_partsUnresolved = partsResolved.invert();
            m_aStoreNewest    = aStoreNewest;
            m_fSharedStorage  = fSharedStorage;
            m_mapCleanup      = mapOwnershipCleanup;

            return mapOwnershipRecover;
            }

        // ----- data members -----------------------------------------------

        /**
         * The Map of registered PersistentStoreInfos, keyed by member.
         */
        protected final Map<Member, PersistentStoreInfo[]> f_mapStoreInfo;

        /**
         * The partition-count.
         */
        protected int m_cPartitions;

        /**
         * The resolved list of the newest stores, indexed by partition-id.
         */
        protected PersistentStoreInfo[] m_aStoreNewest;

        /**
         * The map of member to a list of GUIDs to be deleted.
         */
        protected Map<Member, List<String>> m_mapCleanup;

        /**
         * The resolved map of members to the associated set of partitions.
         */
        protected Map<Member, PartitionSet> m_mapResolved;

        /**
         * The PartitionSet containing partitions that don't have corresponding GUIDs.
         */
        protected PartitionSet m_partsUnresolved;

        /**
         * Specifies whether or not the storage is shared. If true,
         * every member can see all the newest partition stores.
         */
        protected boolean m_fSharedStorage;
        }

    // ----- application entry point ----------------------------------------

    /**
     * Utility that outputs information about a given GUID.
     * <br>
     * Usage: com.tangosol.persistence.GUIDHelper &lt;GUID&gt;
     *
     * @param asArg  command line arguments
     */
    public static void main(String[] asArg)
        {
        if (asArg.length > 0)
            {
            String sGUID = asArg[0];
            out("Partition:  " + getPartition(sGUID));
            out("Version:    " + getVersion(sGUID));
            out("Timestamp:  " + new Date(getServiceJoinTime(sGUID)));
            out("Member ID:  " + getMemberId(sGUID));
            }
        else
            {
            out("Usage: com.tangosol.persistence.GUIDHelper <GUID>");
            }
        }
    }
