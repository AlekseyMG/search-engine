package searchengine.dto.statistics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LemmaCount {

    private static Map<Integer, AtomicInteger> sitesLemmaCounts = Collections.synchronizedMap(new HashMap<>());

    public static void addSiteId(int siteId) {
        sitesLemmaCounts.put(siteId, new AtomicInteger(0));
    }
    public static void increaseLemmaCountForSite(int siteId, int num) {
        int increased = sitesLemmaCounts.get(siteId).get() + num;
        sitesLemmaCounts.replace(siteId, new AtomicInteger(increased));
    }

    public static void decreaseLemmaCountForSite(int siteId, int num) {
        int increased = sitesLemmaCounts.get(siteId).get() - num;
        sitesLemmaCounts.replace(siteId, new AtomicInteger(increased));
    }
    public static int getLemmaCountBySiteId(int siteId) {
        return sitesLemmaCounts.get(siteId).get();
    }
}
