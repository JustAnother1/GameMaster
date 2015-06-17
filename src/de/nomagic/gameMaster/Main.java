package de.nomagic.gameMaster;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nomagic.clientlib.ChangeUserCommand;
import de.nomagic.clientlib.DataReply;
import de.nomagic.clientlib.ListUsersCommand;
import de.nomagic.clientlib.LoginCommand;
import de.nomagic.clientlib.LogoutCommand;
import de.nomagic.clientlib.OkReply;
import de.nomagic.clientlib.RegisterCommand;
import de.nomagic.clientlib.ServerConnection;
import de.nomagic.clientlib.ServerReply;
import de.nomagic.clientlib.libTools;

public class Main
{
    private final Logger log = (Logger) LoggerFactory.getLogger(this.getClass().getName());

    // configuration:
    private String ServerURL = "127.0.0.1";
    private int ServerPort = 4223;
    private ServerConnection con = new ServerConnection();

    private String UserName = "GameMaster";
    private String UserPassword = "doYouWantToPlay";

    public Main()
    {
    }

    private void startLogging(final String[] args)
    {
        int numOfV = 0;
        for(int i = 0; i < args.length; i++)
        {
            if(true == "-v".equals(args[i]))
            {
                numOfV ++;
            }
        }

        // configure Logging
        switch(numOfV)
        {
        case 0: libTools.setLogLevel("warn"); break;
        case 1: libTools.setLogLevel("debug");break;
        case 2:
        default:
            libTools.setLogLevel("trace");
            System.out.println("Build from " + getCommitID());
            break;
        }
    }

    public static String getCommitID()
    {
        try
        {
            final InputStream s = Main.class.getResourceAsStream("/commit-id");
            final BufferedReader in = new BufferedReader(new InputStreamReader(s));
            final String commitId = in.readLine();
            final String changes = in.readLine();
            if(null != changes)
            {
                if(0 < changes.length())
                {
                    return commitId + "-(" + changes + ")";
                }
                else
                {
                    return commitId;
                }
            }
            else
            {
                return commitId;
            }
        }
        catch( Exception e )
        {
            return e.toString();
        }
    }

    public void getConfigFromCommandLine(String[] args)
    {
        for(int i = 0; i < args.length; i++)
        {
            if(true == args[i].startsWith("-"))
            {
                if(true == "-host".equals(args[i]))
                {
                    i++;
                    ServerURL = args[i];
                }
                else if(true == "-port".equals(args[i]))
                {
                    i++;
                    ServerPort = Integer.parseInt(args[i]);
                }
                else if(true == "-v".equals(args[i]))
                {
                    // ignored here -> parsed in startLogging()
                }
                else
                {
                    System.err.println("Invalid Parameter : " + args[i]);
                }
            }
        }
    }

    public static void main(String[] args)
    {
        Main m = new Main();
        m.startLogging(args);
        m.getConfigFromCommandLine(args);
        m.doTheWork();
    }


    private void doTheWork()
    {
        // Connect to server
        con.connectTo(ServerURL, ServerPort);
        if(false == con.isConnected())
        {
            log.error("Could not connect to Server !");
            System.exit(1);
        }
        // login
        ServerReply rep = con.sendCommand(new LoginCommand(UserName, UserPassword));
        if( ! (rep instanceof OkReply))
        {
            // lets try to register with this server
            rep = con.sendCommand(new RegisterCommand(UserName, UserPassword));
            if( ! (rep instanceof OkReply))
            {
                log.error("Reply: " + rep.toString());
                log.error("Could not login !");
                System.exit(2);
            }
            else
            {
                rep = con.sendCommand(new LoginCommand(UserName,UserPassword));
                if( ! (rep instanceof OkReply))
                {
                    log.error("Reply: " + rep.toString());
                    log.error("Could not login !");
                    System.exit(2);
                }
            }
        }
        rep = con.sendCommand(new ChangeUserCommand(UserName, "human", false));

        // Check what we want to do
        // start matches
        // invite players
        // collects results
        rep = con.sendCommand(new ListUsersCommand("Tic-Tac-Toe"));
        if(!(rep instanceof DataReply))
        {
            log.error("Could not read the list of Users !");
        }
        else
        {
            DataReply dr = (DataReply) rep;
            String[] userList = dr.getGeneralData();
            Tournament t = new Tournament(userList, "Tic-Tac-Toe", con);
            t.doTournament();
        }
        // logout
        rep = con.sendCommand(new LogoutCommand());
        // I don't care if the logout worked
        // close connection
        con.disconnect();
        log.info("Done !");
    }

}
