package com.znl.modules.arena;

import akka.actor.Props;
import akka.japi.Creator;
import com.znl.base.BaseLog;
import com.znl.base.BasicModule;
import com.znl.core.*;
import com.znl.define.*;
import com.znl.framework.socket.Request;
import com.znl.msg.GameMsg;
import com.znl.pojo.db.Report;
import com.znl.proto.*;
import com.znl.proxy.*;
import com.znl.utils.GameUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2015/12/4.
 */
public class ArenaModule extends BasicModule {

    public static Props props(final GameProxy gameProxy) {
        return Props.create(new Creator<ArenaModule>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ArenaModule create() throws Exception {
                return new ArenaModule(gameProxy);
            }
        });
    }

    public ArenaModule(GameProxy gameProxy) {
        this.setGameProxy(gameProxy);
        this.setModuleId(ProtocolModuleDefine.NET_M20);
    }

    @Override
    public void onReceiveOtherMsg(Object anyRef) {
        if (anyRef instanceof GameMsg.GetAllArenaRankSuceess) {

        } else if (anyRef instanceof GameMsg.GetAllArenaInfosSucess) {
            M20.M200000.S2C.Builder builder = ((GameMsg.GetAllArenaInfosSucess) anyRef).build();
            int rs=((GameMsg.GetAllArenaInfosSucess) anyRef).rs();
            TimerdbProxy timerdbProxy = getProxy(ActorDefine.TIMERDB_PROXY_NAME);
            int challangeTimes = timerdbProxy.getTimerNum(TimerDefine.ARENA_TIMES, 0, 0);
            int buytimes = timerdbProxy.getTimerNum(TimerDefine.ARENA_FIGHT_TIMES, 0, 0);
            List<Integer> rivals = ((GameMsg.GetAllArenaInfosSucess) anyRef).rivals();
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            playerProxy.rivalsCache = new ArrayList<Integer>();
            playerProxy.rivalsCache.addAll(rivals);
            builder.setChallengetimes(ArenaDefine.FIGHTTIME-challangeTimes);
            Map<Long, Integer> ranks = ((GameMsg.GetAllArenaInfosSucess) anyRef).arenMap();
            List<SimplePlayer> simplePlayers = ((GameMsg.GetAllArenaInfosSucess) anyRef).simplePlayers();
            ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
            int state = timerdbProxy.getTimerNum(TimerDefine.LASTARENAREWAED, 0, 0);
            if (state == 0) {
                if(builder.getLasttimes()==-1) {
                    builder.setLastReward(0);
                }else{
                    builder.setLastReward(1);
                }
            } else {
                builder.setLastReward(2);
            }
            builder.setRs(rs);
            if(rs>=0) {
                builder.addAllFightInfos(arenaProxy.getFightInfos(simplePlayers, ranks));
            }
            builder.setBuytimes(buytimes);
            int bunum = timerdbProxy.getTimerNum(TimerDefine.ARENA_ADD_TIMES, 0, 0);
            JSONObject jsonObject = ConfigDataProxy.getConfigInfoFindByOneKey(DataDefine.ARENA_PRICE, "times", bunum + 1);
            builder.setMoney(jsonObject.getInt("goldprice"));
            pushNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200000, builder.build());
            sendPushNetMsgToClient();
        } else if (anyRef instanceof GameMsg.GetPlayerSimpleInfoListSuccess) {
            List<SimplePlayer> simplePlayers = ((GameMsg.GetPlayerSimpleInfoListSuccess) anyRef).simplePlayer();
            String cmd = ((GameMsg.GetPlayerSimpleInfoListSuccess) anyRef).cmd();
            dulSimpleList(cmd, simplePlayers);
        } else if (anyRef instanceof GameMsg.GetPlayerSimpleInfoSuccess) {
            SimplePlayer simplePlayer = ((GameMsg.GetPlayerSimpleInfoSuccess) anyRef).simplePlayer();
            String cmd = ((GameMsg.GetPlayerSimpleInfoSuccess) anyRef).cmd();
            doSucessSimple(simplePlayer, cmd);
        } else if (anyRef instanceof GameMsg.askChangeInfo) {
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            GameMsg.GetPlayerSimpleInfo mess = new GameMsg.GetPlayerSimpleInfo(playerProxy.getPlayerId(), ArenaDefine.CMD_REFRESHCAPITY);
            sendServiceMsg(ActorDefine.PLAYER_SERVICE_NAME, mess);
        } else if (anyRef instanceof GameMsg.getLaskRankSucess) {
            int rank = ((GameMsg.getLaskRankSucess) anyRef).rank();
            ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
            PlayerReward reward = new PlayerReward();
            int rs = arenaProxy.getLaskArenaReward(rank, reward);
            M20.M200005.S2C.Builder builder = M20.M200005.S2C.newBuilder();
            builder.setRs(rs);
            if(rs==0){
                RewardProxy rewardProxy = getProxy(ActorDefine.REWARD_PROXY_NAME);
                M2.M20007.S2C build20007 = rewardProxy.getRewardClientInfo(reward);
                pushNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20007, build20007);
                List<Integer> powerList=new ArrayList<Integer>();
                for(int power:reward.addPowerMap.keySet()){
                    powerList.add(power);
                }
                M2.M20002.S2C different = sendDifferent(powerList);
                pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20002, different);
            }
            pushNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200005, builder.build());
            sendPushNetMsgToClient();
            send200000();
        }else if (anyRef instanceof GameMsg.sendAreaInfo) {
             send200000();
        }else if(anyRef instanceof GameMsg.NewArenaReportNotify){
            OnTriggerNet200100Event(null);
        }else if(anyRef instanceof GameMsg.GetAllServerArenaReportBack){
            List<Report> list = ((GameMsg.GetAllServerArenaReportBack) anyRef).reports();
            sendReportListToCilent(list);
        }else if(anyRef instanceof GameMsg.GetOneServerArenaReportBack){
            Report report = ((GameMsg.GetOneServerArenaReportBack) anyRef).report();
            sendReportDetailInfoToCilent(report);
        }
    }



    private void sendReportListToCilent(List<Report> list) {
        pushArenaReportToClient(list);
    }


    public void doSucessSimple(SimplePlayer simplePlayer, String cmd) {
        if (cmd.equals(ArenaDefine.CMD_REFRESHCAPITY)) {
            ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
            arenaProxy.resetArenaCapity(simplePlayer);
            updateSimplePlayerData(simplePlayer);
        }
    }


    private void dulSimpleList(String cmd, List<SimplePlayer> simplePlayers) {
        if (ArenaDefine.CMD_ADD_PROTIME.equals(cmd)) {
            changePlayerProTime(simplePlayers);
        } else if (ArenaDefine.CMD_ASK_FIGHT.equals(cmd)) {
            ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            SimplePlayer mysimple = null;
            SimplePlayer rivalSimple = null;
            for (SimplePlayer simplePlayer : simplePlayers) {
                if (simplePlayer.getId() == playerProxy.getPlayerId()) {
                    mysimple = simplePlayer;
                } else {
                    rivalSimple = simplePlayer;
                }
            }
            M20.M200001.S2C.Builder builder = M20.M200001.S2C.newBuilder();
            int rs = arenaProxy.askFight(rivalSimple, mysimple);
            builder.setRs(rs);
            pushNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200001, builder.build());
            sendPushNetMsgToClient();
            if (rs == 0) {
                GameMsg.GetSimplePlayerBysection msg = new GameMsg.GetSimplePlayerBysection(rivalSimple.getId(), playerProxy.getPlayerId(), ArenaDefine.CMD_ADD_PROTIME,new PlayerBattle());
                sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME, msg);
            }
        }
    }


    //更新玩家的保护时间
    public void changePlayerProTime(List<SimplePlayer> simplePlayers) {
        ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
        for (SimplePlayer simplePlayer : simplePlayers) {
            if(simplePlayer.getId()<0){
                continue;
            }
            arenaProxy.setProTime(simplePlayer.getArenaTroop());
            PlayerTroop playerTroop = simplePlayer.getArenaTroop();
            playerTroop.setProtime(GameUtils.getServerDate().getTime() + ArenaDefine.ARENA_TIME_PROTECT);
            simplePlayer.setArenaTroop(playerTroop);
            updateSimplePlayerData(simplePlayer);
        }

    }


    private void OnTriggerNet200000Event(Request request) {
        send200000();
    }

    private void OnTriggerNet200001Event(Request request) {
        M20.M200001.C2S c2s = request.getValue();
        int rank = c2s.getRank();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        GameMsg.askFight msg = new GameMsg.askFight(rank, playerProxy.getPlayerId());
        sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME, msg);
    }
/*


    private void OnTriggerNet200002Event(Request request) {
        M20.M200002.C2S c2s = request.getValue();
        List<Common.FightElementInfo> list = c2s.getFeisList();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        GameMsg.RestArena msg = new GameMsg.RestArena(list, 1, playerProxy.getPlayerId(), playerProxy.getAreaKey());
        sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME, msg);
    }
*/

    private void OnTriggerNet200005Event(Request request) {
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        GameMsg.getLaskRank msg = new GameMsg.getLaskRank(playerProxy.getPlayerId());
        sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME, msg);
    }

    private void test() {
     send200000();
    }


    private void OnTriggerNet200003Event(Request request) {
        ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
        M20.M200003.S2C.Builder builder = M20.M200003.S2C.newBuilder();
        int rs = arenaProxy.addArenaFightTimes();
        builder.setRs(rs);
        TimerdbProxy timerdbProxy = getProxy(ActorDefine.TIMERDB_PROXY_NAME);
        builder.setBuytimes(timerdbProxy.getTimerNum(TimerDefine.ARENA_ADD_TIMES, 0, 0));
        builder.setChallengetimes(5-timerdbProxy.getTimerNum(TimerDefine.ARENA_FIGHT_TIMES, 0, 0));
        sendNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200003, builder.build());
        send200000();
    }

    private void OnTriggerNet200004Event(Request request) {
        M20.M200004.C2S c2s = request.getValue();
        int id = c2s.getId();
        ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
        M20.M200004.S2C.Builder builder = M20.M200004.S2C.newBuilder();
        PlayerReward reward = new PlayerReward();
        int rs = arenaProxy.arenaShopBuy(id, reward);
        builder.setRs(rs);
        builder.setId(id);
        checkPlayerPowerValues(request.getPowerMap());
        sendNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200004, builder.build());
        if (rs == 0) {
            RewardProxy rewardProxy = getProxy(ActorDefine.REWARD_PROXY_NAME);
            M2.M20007.S2C msg = rewardProxy.getRewardClientInfo(reward);
            sendNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20007, msg);
            sendFuntctionLog(FunctionIdDefine.ARENA_SHOP_BUY_FUNCTION_ID);
        }
    }


    public void send200000() {
        M20.M200000.S2C.Builder builder = M20.M200000.S2C.newBuilder();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        GameMsg.GetAllArenaInfos msg = new GameMsg.GetAllArenaInfos(playerProxy.getPlayerId(), "200000");
        sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME, msg);

    }

    private void OnTriggerNet200006Event(Request request) {
        M20.M200006.S2C.Builder builder = M20.M200006.S2C.newBuilder();
        ArenaProxy arenaProxy = getProxy(ActorDefine.ARENA_PROXY_NAME);
        int rs= arenaProxy.removefightTime();
        builder.setRs(rs);
        sendNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200006, builder.build());
        if(rs==0){
            SystemProxy systemProxy=getProxy(ActorDefine.SYSTEM_PROXY_NAME);
            GameMsg.SystemTimer message = new GameMsg.SystemTimer();
            sendModuleMsg(ActorDefine.SYSTEM_MODULE_NAME, message);
        }
    }

    private void pushArenaReportToClient(List<Report> list){
        M20.M200100.S2C.Builder builder = M20.M200100.S2C.newBuilder();
        MailProxy mailProxy = getProxy(ActorDefine.MAIL_PROXY_NAME);
        builder.addAllPerInfos(mailProxy.getAllArenaReoprtShortInfo());
        builder.addAllAllInfos(mailProxy.getServerArenaReportShortInfo(list));
        pushNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200100, builder.build());
        sendPushNetMsgToClient();
    }

    private void OnTriggerNet200100Event(Request request) {
        sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME,new GameMsg.GetAllServerArenaReport());
    }

    private void OnTriggerNet200101Event(Request request) {
        M20.M200101.C2S c2s = request.getValue();
        long id = c2s.getId();
        int type = c2s.getType();
        if (type == 1){
            //个人
            MailProxy mailProxy = getProxy(ActorDefine.MAIL_PROXY_NAME);
            boolean read = mailProxy.isReadReport(id);
            M20.PerDetailInfos detail = mailProxy.getArenaReoprtDetailInfoById(id);
            M20.M200101.S2C.Builder builder = M20.M200101.S2C.newBuilder();
            builder.setInfos(detail);
            builder.setId(id);
            sendNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200101, builder.build());
            if (read == false){
                OnTriggerNet200100Event(null);
            }
            sendFuntctionLog(FunctionIdDefine.GET_ARENA_REPORT_DETAIL_INFO_FUNCTION_ID);
        }else {
            sendServiceMsg(ActorDefine.ARENA_SERVICE_NAME,new GameMsg.GetOneServerArenaReport(id));
        }
    }

    private void sendReportDetailInfoToCilent(Report report) {
        M20.M200101.S2C.Builder builder = M20.M200101.S2C.newBuilder();
        MailProxy mailProxy = getProxy(ActorDefine.MAIL_PROXY_NAME);
        M20.PerDetailInfos.Builder detailInfo = M20.PerDetailInfos.newBuilder(mailProxy.getArenaReoprtDetailInfo(report));
        detailInfo.setType(3);
        builder.setInfos(detailInfo.build());
        builder.setId(report.getId());
        pushNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200101, builder.build());
        sendPushNetMsgToClient();
        sendFuntctionLog(FunctionIdDefine.GET_ARENA_REPORT_DETAIL_INFO_FUNCTION_ID);
    }

    private void OnTriggerNet200102Event(Request request) {
        M20.M200102.C2S c2s = request.getValue();
        List<Long> id = c2s.getIdList();
        MailProxy mailProxy = getProxy(ActorDefine.MAIL_PROXY_NAME);
        int rs = mailProxy.deleteArenaReport(id);
        M20.M200102.S2C.Builder builder = M20.M200102.S2C.newBuilder();
        builder.setRs(rs);
        sendNetMsg(ProtocolModuleDefine.NET_M20, ProtocolModuleDefine.NET_M20_C200102, builder.build());
        OnTriggerNet200100Event(null);
    }

    /**
     * 重复协议请求处理
     * @param cmd
     */
    @Override
    public void repeatedProtocalHandler(int cmd) {

    }
}