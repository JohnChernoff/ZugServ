import org.chernovia.lib.zugserv.ConnListener;
import org.chernovia.lib.zugserv.Connection;
import org.chernovia.lib.zugserv.ZugServ;
import org.chernovia.lib.zugserv.web.WebSockServ;

public class ServTest implements ConnListener {

    public static void main(String[] args) {
        new ServTest();
    }

    public ServTest() {
        WebSockServ serv = new WebSockServ(7777,this);
        serv.startSrv();
    }

    @Override
    public void newMsg(Connection conn, int channel, String msg) {
        log(conn.getID() + " on chan " + channel + ": " + msg);
        conn.tell(ZugServ.MSG_SERV,"You said: " + msg);
    }

    @Override
    public void loggedIn(Connection conn) {
        log("Logged in: " + conn);
    }

    @Override
    public void loggedOut(Connection conn) {
        log("Logged out: " + conn);
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
