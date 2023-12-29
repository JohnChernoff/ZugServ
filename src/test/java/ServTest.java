import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.web.WebSockServ;
import org.kraweki.lib.db.OwenBase;

public class ServTest implements ConnListener {

    TestArea area;

    public static void main(String[] args) {
        new ServTest();
    }

    public ServTest() {
        WebSockServ serv = new WebSockServ(1234,this);
        new OwenBase("localhost:3306", "testUser", "testPass", "test");
        serv.startSrv();
    }

    @Override
    public void newMsg(Connection conn, int channel, String msg) {
        log(conn.getID() + " on chan " + channel + ": " + msg);

        TestUser user = new TestUser(conn,"New user");
        user.tell(ZugServ.MsgTypes.serv_msg,"You said: " + msg);

        area = new TestArea("TestArea", user); log("Adding: " + user.addArea(area));

        TestOccupant occupant = new TestOccupant(user,area); area.addOrGetOccupant(occupant);

        if (occupant.eq(new TestOccupant(user,area))) occupant.err(occupant.toJSON().textValue());

        user.tell(ZugServ.MsgTypes.serv_msg,user.toJSON());
        user.tell(ZugServ.MsgTypes.login);
        log("Logged in: " + user.isLoggedIn());

    }

    @Override
    public void connected(Connection conn) {
        log("Connected: " + conn);
    }

    @Override
    public void disconnected(Connection conn) {
        log("Disconnected: " + conn);
    }

    public void log(String msg) {
        System.out.println(msg);
    }
}

class TestUser extends ZugUser {
    public TestUser(Connection c, String n) {
        super(c, n);
    }
    @Override
    public ObjectNode toJSON() {
        return null;
    }
}

class TestOccupant extends Occupant {

    public TestOccupant(TestUser u, TestArea a) {
        super(u, a);
    }

    @Override
    public ObjectNode toJSON() {
        return null;
    }
}

class TestArea extends ZugArea {

    public TestArea(String t, TestUser c) {
        super(t, c);
    }

    @Override
    public void msg(ZugUser user, String msg) {

    }

    @Override
    public void err(ZugUser user, String msg) {

    }

    @Override
    public void log(String msg) {

    }

    @Override
    public ObjectNode toJSON() {
        return null;
    }
}
