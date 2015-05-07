package cc.blynk.server.model;

import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.model.widgets.others.Mail;
import cc.blynk.server.model.widgets.others.Timer;
import cc.blynk.server.notifications.twitter.model.TwitterAccessToken;
import cc.blynk.server.utils.JsonParser;

import java.util.*;


/**
 * User: ddumanskiy
 * Date: 21.11.13
 * Time: 13:04
 */
public class Profile {

    private DashBoard[] dashBoards;

    private TwitterAccessToken twitter;

    private Map<Integer, Set<Byte>> graphPins;

    private transient Integer activeDashId;
    /**
     * Specific property used for improving user experience on mobile application.
     * In case user activated dashboard before hardware connected to server, user have to
     * deactivate and activate dashboard again in order to setup PIN MODES (OUT, IN).
     * With this property problem resolved by server side. Command for setting Pin Modes
     * is remembered and when hardware goes online - server sends Pin Modes command to hardware
     * without requiring user to activate/deactivate dashboard again.
     */
    private transient HardwareMessage pinModeMessage;


    public void validateDashId(int dashBoardId, int msgId) {
        if (dashBoards != null) {
            for (DashBoard dashBoard : dashBoards) {
                if (dashBoard.getId() == dashBoardId) {
                    return;
                }
            }
        }

        throw new IllegalCommandException(String.format("Requested token for non-existing '%d' dash id.", dashBoardId), msgId);
    }

    public DashBoard[] getDashBoards() {
        return dashBoards;
    }

    public void setDashBoards(DashBoard[] dashBoards) {
        this.dashBoards = dashBoards;
    }

    public List<Timer> getActiveDashboardTimerWidgets() {
        if (dashBoards == null || dashBoards.length == 0 || activeDashId == null) {
            return Collections.emptyList();
        }

        DashBoard dashBoard = getActiveDashBoard();
        if (dashBoard == null) {
            return Collections.emptyList();
        }

        return dashBoard.getTimerWidgets();
    }

    public Mail getActiveDashboardEmailWidget() {
        if (dashBoards == null || dashBoards.length == 0 || activeDashId == null) {
            return null;
        }

        DashBoard dashBoard = getActiveDashBoard();
        if (dashBoard == null) {
            return null;
        }

        return dashBoard.getEmailWidget();
    }

    public DashBoard getActiveDashBoard() {
        for (DashBoard dashBoard : dashBoards) {
            if (dashBoard.getId() == activeDashId) {
                return dashBoard;
            }
        }
        return null;
    }

    public void calcGraphPins() {
        if (dashBoards == null || dashBoards.length == 0) {
            graphPins = Collections.emptyMap();
            return;
        }

        graphPins = new HashMap<>();

        for (DashBoard dashBoard : dashBoards) {
            graphPins.put(dashBoard.getId(), dashBoard.getGraphWidgetPins());
        }
    }

    public boolean hasGraphPin(Integer dashId, Byte pin) {
        Set<Byte> pins = graphPins.get(dashId);
        return pins != null && pins.contains(pin);
    }

    public Map<Integer, Set<Byte>> getGraphPins() {
        return graphPins;
    }

    public void setGraphPins(Map<Integer, Set<Byte>> graphPins) {
        this.graphPins = graphPins;
    }

    public TwitterAccessToken getTwitter() {
        return twitter;
    }

    public void setTwitter(TwitterAccessToken twitter) {
        this.twitter = twitter;
    }

    public Integer getActiveDashId() {
        return activeDashId;
    }

    public void setActiveDashId(Integer activeDashId) {
        this.activeDashId = activeDashId;
    }

    public HardwareMessage getPinModeMessage() {
        return pinModeMessage;
    }

    public void setPinModeMessage(HardwareMessage pinModeMessage) {
        this.pinModeMessage = pinModeMessage;
    }

    @Override
    public String toString() {
        return JsonParser.toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Profile that = (Profile) o;

        if (!Arrays.equals(dashBoards, that.dashBoards)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return dashBoards != null ? Arrays.hashCode(dashBoards) : 0;
    }
}
