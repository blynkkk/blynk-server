package cc.blynk.server.model.auth;

import cc.blynk.server.model.DashBoard;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 01.05.15.
 */
public class UserTest {

    @Test
    public void putTokenTest() {
        User user = new User();
        user.putToken(1, "1");
        assertEquals(1, user.getDashTokens().size());
    }

    @Test
    public void putTokenTest2() {
        User user = new User();
        user.getDashTokens().put(222, "1");
        user.getDashTokens().put(333, "2");
        DashBoard dashBoard = new DashBoard();
        dashBoard.setId(1);
        user.getProfile().setDashBoards(new DashBoard[]{dashBoard});
        user.putToken(1, "1");

        assertEquals(1, user.getDashTokens().size());
        assertEquals("1", user.getDashTokens().get(1));
    }

}
