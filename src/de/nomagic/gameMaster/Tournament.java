package de.nomagic.gameMaster;

import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nomagic.clientlib.CreateMatchCommand;
import de.nomagic.clientlib.DataReply;
import de.nomagic.clientlib.DeleteMatchCommand;
import de.nomagic.clientlib.MessageType;
import de.nomagic.clientlib.OkReply;
import de.nomagic.clientlib.RequestMatchDataCommand;
import de.nomagic.clientlib.SendMessageCommand;
import de.nomagic.clientlib.ServerConnection;
import de.nomagic.clientlib.ServerReply;

public class Tournament
{
    private final Logger log = (Logger) LoggerFactory.getLogger(this.getClass().getName());

    public final static int ROUNDS = 100;

    private int numParticipants;
    private int matchesStarted = 0;
    private int matchesEnded = 0;
    private int [] gamesWon;
    private int [] gamesLost;
    private int [] gamesUndecided;
    private String[] participants;
    private String GameType;
    private Vector<String> ongoingMatches = new Vector<String>();
    private ServerConnection con;
    private boolean valid = false;

    public Tournament(String[] participants, String GameType, ServerConnection con)
    {
        this.participants = participants;
        this.GameType = GameType;
        this.con = con;
        if(null == participants)
        {
            log.error("Participants are null !");
            return;
        }
        if(null == GameType)
        {
            log.error("game type is null !");
            return;
        }
        if(null == con)
        {
            log.error("connection is null !");
            return;
        }
        numParticipants = participants.length;
        log.info("Found {} Players !", numParticipants);
        gamesWon = new int[numParticipants];
        gamesLost = new int[numParticipants];
        gamesUndecided = new int[numParticipants];
        for(int i = 0; i < numParticipants; i++)
        {
            gamesWon[i] = 0;
            gamesLost[i] = 0;
            gamesUndecided[i] = 0;
        }
        valid = true;
    }

    public void doTournament()
    {
        if(true == valid)
        {
            startGames();
            log.trace("Matches Started : {}", matchesStarted);
            log.trace("Matches Ended   : {}", matchesEnded);
            log.trace("Matches active   : {}", ongoingMatches.size());
            while(0 < ongoingMatches.size())
            {
                handleFinishedGames();
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    // I really don't care
                }
            }
            printResults();
        }
    }


    private void startGames()
    {
        for(int round = 0; round < ROUNDS; round++)
        {
            for(int firstPlayerIdx = 0; firstPlayerIdx < numParticipants; firstPlayerIdx++)
            {
                for(int secondPlayerIdx = 0; secondPlayerIdx < numParticipants; secondPlayerIdx ++)
                {
                    if(firstPlayerIdx != secondPlayerIdx)
                    {
                        String MatchName = participants[firstPlayerIdx]
                                           + "-vs-" + participants[secondPlayerIdx]
                                           + "-Round" + round;
                        // try to delete the match
                        ServerReply rep = con.sendCommand(new DeleteMatchCommand(GameType, MatchName));
                        // result is not important
                        // create the match
                        rep = con.sendCommand(new CreateMatchCommand(GameType, MatchName));
                        if( ! (rep instanceof OkReply))
                        {
                            log.error("Reply: " + rep.toString());
                            log.error("Could not create Game !");
                            return;
                        }
                        // invite them to play
                        rep = con.sendCommand(new SendMessageCommand(participants[firstPlayerIdx], MessageType.INVITE, GameType + " " + MatchName));
                        if( ! (rep instanceof OkReply))
                        {
                            log.error("Reply: " + rep.toString());
                            log.error("Could not invite Player !");
                            return;
                        }
                        rep = con.sendCommand(new SendMessageCommand(participants[secondPlayerIdx], MessageType.INVITE, GameType + " " + MatchName));
                        if( ! (rep instanceof OkReply))
                        {
                            log.error("Reply: " + rep.toString());
                            log.error("Could not invite Player !");
                            return;
                        }
                        ongoingMatches.add(MatchName);
                        matchesStarted ++;
                    }
                    else
                    {
                        // no match needed
                    }
                }
            }
        }
    }

    private void printResults()
    {
        log.info("Matches Started : {}", matchesStarted);
        log.info("Matches Ended   : {}", matchesEnded);
        log.info("  Won | undecided | lost  | Name");
        log.info("------+-----------+-------+-----");
        for(int i = 0; i < numParticipants; i++)
        {
            log.info(String.format("%5d |   %5d   | %5d | %s", gamesWon[i],
                                                               gamesUndecided[i],
                                                               gamesLost[i],
                                                               participants[i]));
        }
        log.info("Done !");
    }

    private void handleFinishedGames()
    {
        for(int i = 0; i < ongoingMatches.size(); i++)
        {
            String matchName = ongoingMatches.get(i);
            ServerReply rep = con.sendCommand(new RequestMatchDataCommand(GameType, matchName));
            if(!(rep instanceof DataReply))
            {
                log.error("Could not check on the Match({}) !", matchName);
                ongoingMatches.remove(i);
                i--;
                continue;
            }
            else
            {
                DataReply dr = (DataReply) rep;
                if(false == dr.getBooleanValueOf("is ongoing"))
                {
                    String Player1 =  dr.getStringValueOf("Player 1");
                    String Player2 =  dr.getStringValueOf("Player 2");
                    String winnerName = dr.getStringValueOf("won by");
                    if("none".equals(winnerName))
                    {
                        // undecided game
                        gamesUndecided[getIndexOf(Player1, participants)]++;
                        gamesUndecided[getIndexOf(Player2, participants)]++;
                    }
                    else
                    {
                        if(true == Player1.equals(winnerName))
                        {
                            gamesWon[getIndexOf(Player1, participants)]++;
                            gamesLost[getIndexOf(Player2, participants)]++;
                        }
                        else if(true == Player2.equals(winnerName))
                        {
                            gamesWon[getIndexOf(Player2, participants)]++;
                            gamesLost[getIndexOf(Player1, participants)]++;
                        }
                        else
                        {
                            log.error("Could detect winner of Match ({}) !", matchName);
                            ongoingMatches.remove(i);
                            i--;
                            continue;
                        }
                    }
                    // deleteMatchData
                    rep = con.sendCommand(new DeleteMatchCommand(GameType, matchName));
                    if(!(rep instanceof DataReply))
                    {
                        log.error("Could not check on the Match({}) !", matchName);
                        ongoingMatches.remove(i);
                        i--;
                        continue;
                    }
                    ongoingMatches.remove(i);
                    i--;
                    matchesEnded ++;
                }
                // else game not finished yet - wait
            }
        }
    }

    private int getIndexOf(String val, String[] list)
    {
        for(int i = 0; i < list.length; i++)
        {
            if(true == val.equals(list[i]))
            {
                return i;
            }
        }
        log.error("Could not find _{}_ !", val);
        return list.length + 100;
    }

}
