package cc.blynk.server.dao;

import cc.blynk.common.utils.StringUtils;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.model.auth.User;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import static cc.blynk.common.model.messages.protocol.HardwareMessage.attachTS;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/18/2015.
 *
 * todo redesign. right now it is not efficient at all
 */
public class GraphInMemoryStorage implements Storage {

    private final Map<String, Queue<String>> userValues;
    private final int sizeLimit;

    public GraphInMemoryStorage(int sizeLimit) {
        this.userValues = new ConcurrentHashMap<>();
        this.sizeLimit = sizeLimit;
    }

    @Override
    public String store(User user, Integer dashId, String body, int msgId) {
        if (body.length() < 4) {
            throw new IllegalCommandException("Hardware command body too short.", msgId);
        }

        if (body.charAt(1) == 'w') {
            Byte pin;
            try {
                pin = Byte.valueOf(StringUtils.fetchPin(body));
            } catch (NumberFormatException e) {
                throw new IllegalCommandException("Hardware command body incorrect.", msgId);
            }

            if (user.getProfile().hasGraphPin(dashId, pin)) {
                body = attachTS(body);
                storeValue(user.getName(), dashId, body);
            }
        }
        return body;
    }

    private void storeValue(String userName, Integer dashId, String body) {
        //expecting same user always in same thread, so no concurrency
        String key = userName + dashId;
        Queue<String> bodies = userValues.get(key);
        if (bodies == null) {
            bodies = new LinkedList<>();
            userValues.put(key, bodies);
        }

        if (bodies.size() == sizeLimit) {
            bodies.poll();
        }
        bodies.add(body);
    }

}
