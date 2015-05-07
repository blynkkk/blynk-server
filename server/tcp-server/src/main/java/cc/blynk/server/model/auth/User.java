package cc.blynk.server.model.auth;

import cc.blynk.server.model.DashBoard;
import cc.blynk.server.model.Profile;
import cc.blynk.server.stats.metrics.InstanceLoadMeter;
import cc.blynk.server.utils.JsonParser;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * User: ddumanskiy
 * Date: 8/11/13
 * Time: 4:03 PM
 */
public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;

    private String pass;

    private String id;

    //used mostly to understand if user profile was changed, all other fields update ignored as it is not so important
    private long lastModifiedTs;

    private Profile profile;

    private Map<Integer, String> dashTokens = new HashMap<>();

    //maybe it is bette rto make it transient
    private Stats stats;

    //stats related logic
    //todo move to session?
    private transient InstanceLoadMeter quotaMeter;
    private transient volatile long lastQuotaExceededTime;
    // we set it with time in the past to make first email/tweet/push possible.
    private transient volatile long lastNotificationSentTs;

    public User() {
        this.lastModifiedTs = System.currentTimeMillis();
        this.profile = new Profile();
    }

    public User(String name, String pass) {
        this();
        this.name = name;
        this.pass = pass;
        this.stats = new Stats();
        initQuota();
    }

    public void initQuota() {
        this.quotaMeter = new InstanceLoadMeter();
    }

    public void incrStat(short cmd) {
        stats.incr(cmd);
        quotaMeter.mark();
    }

    public void incrException(int exceptionCode) {
        stats.incrException(exceptionCode);
        quotaMeter.mark();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        //do not accept activeDashId from user, it is calculated field.
        profile.setActiveDashId(this.profile.getActiveDashId());
        this.profile = profile;
        this.lastModifiedTs = System.currentTimeMillis();
    }

    public void putToken(Integer dashId, String token) {
        cleanTokensForNonExistentDashes();
        this.dashTokens.put(dashId, token);
        this.lastModifiedTs = System.currentTimeMillis();
    }

    private void cleanTokensForNonExistentDashes() {
        Iterator<Integer> iterator = this.dashTokens.keySet().iterator();
        while (iterator.hasNext()) {
            if (!exists(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private boolean exists(int dashId) {
        if (profile.getDashBoards() == null) {
            return false;
        }

        for (DashBoard dashBoard : profile.getDashBoards()) {
            if (dashBoard.getId() == dashId) {
                return true;
            }
        }

        return false;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<Integer, String> getDashTokens() {
        return dashTokens;
    }

    public void setDashTokens(Map<Integer, String> dashTokens) {
        this.dashTokens = dashTokens;
    }

    public Stats getStats() {
        return stats;
    }

    public InstanceLoadMeter getQuotaMeter() {
        return quotaMeter;
    }

    public long getLastModifiedTs() {
        return lastModifiedTs;
    }

    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }

    public long getLastQuotaExceededTime() {
        return lastQuotaExceededTime;
    }

    public void setLastQuotaExceededTime(long lastQuotaExceededTime) {
        this.lastQuotaExceededTime = lastQuotaExceededTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

		return !(name != null ? !name.equals(user.name) : user.name != null);

	}

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return JsonParser.toJson(this);
    }

    public long getLasNotificationSentTs() {
        return lastNotificationSentTs;
    }

    public void setLastNotificationSentTs(long lastNotificationSentTs) {
        this.lastNotificationSentTs = lastNotificationSentTs;
    }
}
