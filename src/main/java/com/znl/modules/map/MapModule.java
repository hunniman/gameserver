package com.znl.modules.map;

import akka.actor.Props;
import akka.japi.Creator;
import com.znl.base.BasicModule;
import com.znl.core.*;
import com.znl.define.*;
import com.znl.framework.socket.Request;
import com.znl.log.CustomerLogger;
import com.znl.msg.GameMsg;
import com.znl.pojo.db.PerformTasks;
import com.znl.pojo.db.TeamNotice;
import com.znl.pojo.db.WorldTeamData;
import com.znl.proto.*;
import com.znl.proxy.*;
import com.znl.service.PlayerService;
import com.znl.service.WorldService;
import com.znl.service.map.TileType;
import com.znl.service.map.WorldTile;
import com.znl.template.MailTemplate;
import com.znl.utils.GameUtils;
import com.znl.utils.RandomUtil;
import org.json.JSONObject;
import scala.Tuple2;


import java.util.*;

/**
 * Created by Administrator on 2015/11/12.
 */
public class MapModule extends BasicModule {

    public static Props props(final GameProxy gameProxy) {
        return Props.create(new Creator<MapModule>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MapModule create() throws Exception {
                return new MapModule(gameProxy);
            }
        });
    }

    public MapModule(GameProxy gameProxy) {
        this.setGameProxy(gameProxy);
        this.setModuleId(ProtocolModuleDefine.NET_M8);
        PlayerReward reward = new PlayerReward();
        TaskProxy taskProxy = getProxy(ActorDefine.TASK_PROXY_NAME);
        taskProxy.getTaskUpdate(TaskDefine.TASK_TYPE_WINRESOURCE_LV, 0);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        playerProxy.getPlayer().setGardNum(performTasksProxy.getguardNum());
    }

    @Override
    public void onReceiveOtherMsg(Object object) {

        if (object instanceof GameMsg.WatchBuildingTileInfoBack) {
            onWatchBuildingTileInfoBack((GameMsg.WatchBuildingTileInfoBack) object);
        } else if (object instanceof GameMsg.Watchmagnifyingback) {
            onWatchWatchmagnifyingback((GameMsg.Watchmagnifyingback) object);
        } else if (object instanceof GameMsg.FightBuildResult) {
            GameMsg.FightBuildResult mess = (GameMsg.FightBuildResult) object;
            List<PlayerTeam> teams = ((GameMsg.FightBuildResult) object).attackTeams();
            addLostSoldiers(mess);
        }else if (object instanceof  GameMsg.DefendFightResult){
            List<PlayerTeam> teams = ((GameMsg.DefendFightResult) object).defendList();
            long defendId=((GameMsg.DefendFightResult)object).defendTeamId();
            int honor=((GameMsg.DefendFightResult)object).honor();
            addDefendLostSoldiers(teams,defendId,honor);
        } else if (object instanceof GameMsg.DefendBuildResult) {
            GameMsg.DefendBuildResult mess = (GameMsg.DefendBuildResult) object;
            reduceDefendSoldier(mess);
        } else if (object instanceof GameMsg.BuildPointNotify) {
            GameMsg.BuildPointNotify msg = (GameMsg.BuildPointNotify) object;
            sendBuildPointNotify(msg.id(), msg.x(), msg.y());
        } else if (object instanceof GameMsg.DetectPriceBack) {
            GameMsg.DetectPriceBack mess = (GameMsg.DetectPriceBack) object;
            sendDetectPriceBack(mess);
        } else if (object instanceof GameMsg.GetSpyReportNotify) {
            long id = ((GameMsg.GetSpyReportNotify) object).id();
            sendDetectToClient(id);
        } else if (object instanceof GameMsg.CallBackTaskBack) {
//            pushTaskListToClient();
        } else if (object instanceof GameMsg.StopNodeTeamBack) {
            GameMsg.StopNodeTeamBack mess = (GameMsg.StopNodeTeamBack) object;
            onStopNodeTeamBack(mess);
        } else if (object instanceof GameMsg.MoveWorldBuildBack) {
            GameMsg.MoveWorldBuildBack mess = (GameMsg.MoveWorldBuildBack) object;
            onMoveWorldBuildBack(mess);
        } else if (object instanceof GameMsg.GetPlayerSimpleInfoSuccess) {
            GameMsg.GetPlayerSimpleInfoSuccess mess = (GameMsg.GetPlayerSimpleInfoSuccess) object;
            onGetPlayerSimpleInfoSuccess(mess);
        } else if (object instanceof GameMsg.CollectBack) {
            M8.CollectInfo collectInfo = ((GameMsg.CollectBack) object).collinfo();
            int rs = ((GameMsg.CollectBack) object).rs();
            int ower = ((GameMsg.CollectBack) object).ower();
            M8.M80008.S2C.Builder builder = M8.M80008.S2C.newBuilder();
            CollectProxy collectProxy = getProxy(ActorDefine.COLLECT_PROXY_NAME);
            int nwers = collectProxy.addCollectByrequest(rs, collectInfo, ower);
            builder.setRs(nwers);
            builder.addAllInfos(collectProxy.getCollectInfos());
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80008, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80008);
            if (rs >= 0) {
                sendFuntctionLog(FunctionIdDefine.ADD_COLLECT_FUNCTION_ID, collectInfo.getX(), collectInfo.getY(), 0, collectInfo.getName());
            }
        } else if (object instanceof GameMsg.MoveRandomWorldBuildBack) {
            onMoveRandomWorldBuildBack((GameMsg.MoveRandomWorldBuildBack) object);
        } else if (object instanceof GameMsg.getOrePointback) {
            M8.M80006.S2C.Builder builder = M8.M80006.S2C.newBuilder();
            ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            List<String> list = ((GameMsg.getOrePointback) object).list();
            SimplePlayer simplePlayer = ((GameMsg.getOrePointback) object).simple();
            builder.setRs(0);
            if (list.size() == 0) {
                builder.setRs(ErrorCodeDefine.M80006_5);
                MailTemplate template = new MailTemplate("矿点搜索报告", "使用成功，您查找的指挥官" + simplePlayer.getName() + "没有占领中的矿点", 0, playerProxy.getPlayerName(), ChatAndMailDefine.MAIL_TYPE_INBOX);
                Set<Long> allid = new HashSet<>();
                allid.add(playerProxy.getPlayerId());
                GameMsg.SendMail mail = new GameMsg.SendMail(allid, template, "系统邮件", 0l);
                sendServiceMsg(ActorDefine.MAIL_SERVICE_NAME, mail);
            } else {
                builder.setRs(0);
                int num = RandomUtil.random(0, list.size() - 1);
                String str = list.get(num);
                int x = Integer.parseInt(str.split(",")[0]);
                int y = Integer.parseInt(str.split(",")[1]);
                builder.setX(x);
                builder.setY(y);
                MailTemplate template = new MailTemplate("矿点搜索报告", "使用成功，您查找的指挥官" + simplePlayer.getName() + "，占领中的坐标为" + x + "," + y, 0, playerProxy.getPlayerName(), ChatAndMailDefine.MAIL_TYPE_INBOX);
                Set<Long> allid = new HashSet<>();
                allid.add(playerProxy.getPlayerId());
                GameMsg.SendMail mail = new GameMsg.SendMail(allid, template, "系统邮件", 0l);
                sendServiceMsg(ActorDefine.MAIL_SERVICE_NAME, mail);
            }
            itemProxy.reduceItemNum(ItemDefine.ORE_ITEM_ID, 1, LogDefine.LOST_MOVE_CITY);
            Common.ItemInfo info = itemProxy.getItemInfo(ItemDefine.ORE_ITEM_ID);
            M2.M20007.S2C.Builder itemBuildier = M2.M20007.S2C.newBuilder();
            itemBuildier.addItemList(info);
            pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20007, itemBuildier.build());
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80006, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80006);
        } else if (object instanceof GameMsg.getArenaRankBack) {
            Map<Long, Integer> map = ((GameMsg.getArenaRankBack) object).rankmap();
            String cmd = ((GameMsg.getArenaRankBack) object).cmd();
        } else if (object instanceof GameMsg.finishResouceTask) {
            int levl = ((GameMsg.finishResouceTask) object).lv();
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            playerProxy.setWorldResourceLevel(levl);
            Set<Long> setlist = playerProxy.getWorldResourLevellist();
            setlist.add((long) levl);
            playerProxy.setWorldResourceLevellist(setlist);
            PlayerReward reward = new PlayerReward();
            TaskProxy taskProxy = getProxy(ActorDefine.TASK_PROXY_NAME);
            taskProxy.getTaskUpdate(TaskDefine.TASK_TYPE_WINRESOURCE_LV, 0);
        }else if(object instanceof GameMsg.TeamDataChange){
            long teamId = ((GameMsg.TeamDataChange) object).teamId();
            updateTaskNotify(teamId);
        }else if(object instanceof GameMsg.TeamDataAdd){
            long teamId = ((GameMsg.TeamDataAdd) object).teamId();
            updateTaskNotify(teamId);
        }else if(object instanceof GameMsg.TeamDataDelete){
            WorldTeamData teamData = ((GameMsg.TeamDataDelete) object).team();
            deleteTaskNotify(teamData);
        }else if(object instanceof GameMsg.TeamBack){
            WorldTeamData teamData = ((GameMsg.TeamBack) object).teamData();
            doTeamBackLogic(teamData);
        }
    }




    private void onMoveRandomWorldBuildBack(GameMsg.MoveRandomWorldBuildBack mess) {
        int x = mess.x();
        int y = mess.y();
        M8.M80011.S2C.Builder builder = M8.M80011.S2C.newBuilder();
        builder.setRs(0).setX(x).setY(y);
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        if (itemProxy.getItemId(ItemDefine.MOVE_RANDOM_ITEM_ID) > 0) {
            itemProxy.reduceItemNum(ItemDefine.MOVE_RANDOM_ITEM_ID, 1, LogDefine.LOST_MOVE_CITY);
            Common.ItemInfo info = itemProxy.getItemInfo(ItemDefine.MOVE_RANDOM_ITEM_ID);
            M2.M20007.S2C.Builder itemBuildier = M2.M20007.S2C.newBuilder();
            itemBuildier.addItemList(info);
            pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20007, itemBuildier.build());
        } else {
            CustomerLogger.error("要扣除随机迁城令的时候居然没有了啊啊啊啊！！！");
        }
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80011, builder.build());
        M2.M20014.S2C.Builder pointInfo = M2.M20014.S2C.newBuilder();
        pointInfo.setWorldTileX(x).setWorldTileY(y);
        pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20014, pointInfo.build());
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        performTasksProxy.clearTeamNotice();
        //TODO 发送80107
        OnTriggerNet80107Event(null);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        playerProxy.setWorldTilePoint(x, y);
        updateMySimplePlayerData();
    }

//    private void pushAllTeamNoticeInfo() {
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
//        M8.M80007.S2C.Builder builder = M8.M80007.S2C.newBuilder();
//        List<M8.TeamNoticeInfo> list = performTasksProxy.getAllTeamNoticeInfo();
//        builder.addAllInfos(list);
//        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80007, builder.build());
//        sendPushNetMsgToClient();
//    }

    private void addTeamNotice(TeamNotice notice) {
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        performTasksProxy.addTeamNotices(notice, playerProxy);
        //TODO 发送80108
        pushTeamNoticeToClient(notice.getId());
    }

    private void onGetPlayerSimpleInfoSuccess(GameMsg.GetPlayerSimpleInfoSuccess mess) {
        String cmd = mess.cmd();
        SimplePlayer simplePlayer = mess.simplePlayer();
        if ("80006".equals(cmd)) {
            onCheckPlayerPoint(simplePlayer);
        }
    }

    public void sendRrfrshProtect() {
        M8.M80016.S2C.Builder builder = M8.M80016.S2C.newBuilder();
        builder.setRs(0);
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80016, builder.build());
    }

    /******
     * 查看其它玩家坐标
     ******/
    private void onCheckPlayerPoint(SimplePlayer simplePlayer) {
        M8.M80006.S2C.Builder builder = M8.M80006.S2C.newBuilder();
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        int typeId = itemProxy.pointItem;
        if (simplePlayer == null) {
            builder.setRs(ErrorCodeDefine.M80006_9);
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80006, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80006);
        } else if (typeId == 0) {
            builder.setRs(ErrorCodeDefine.M80006_4);
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80006, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80006);
        } else {
            if (typeId == ItemDefine.CHECK_PLAYER_ITEM_ID) {
                builder.setRs(0);
                builder.setX(simplePlayer.getX());
                builder.setY(simplePlayer.getY());
                //扣除道具
                itemProxy.reduceItemNum(typeId, 1, LogDefine.LOST_MOVE_CITY);
                Common.ItemInfo info = itemProxy.getItemInfo(ItemDefine.CHECK_PLAYER_ITEM_ID);
                M2.M20007.S2C.Builder itemBuildier = M2.M20007.S2C.newBuilder();
                itemBuildier.addItemList(info);
                pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20007, itemBuildier.build());
                pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80006, builder.build());
                sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80006);
                MailTemplate template = new MailTemplate("搜索结果", "使用成功，您查找的指挥官" + simplePlayer.getName() + "，的基地坐标为" + simplePlayer.getX() + "," + simplePlayer.getY(), 0, playerProxy.getPlayerName(), ChatAndMailDefine.MAIL_TYPE_INBOX);
                Set<Long> allid = new HashSet<>();
                allid.add(playerProxy.getPlayerId());
                GameMsg.SendMail mail = new GameMsg.SendMail(allid, template, "系统邮件", 0l);
                sendServiceMsg(ActorDefine.MAIL_SERVICE_NAME, mail);
            } else {
                sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.getOrePoint(simplePlayer, playerProxy.getAccountName()));
            }
        }
        itemProxy.pointItem = 0;
    }


    private void onMoveWorldBuildBack(GameMsg.MoveWorldBuildBack mess) {
        int rs = mess.rs();
        int x = mess.x();
        int y = mess.y();
        M8.M80005.S2C.Builder builder = M8.M80005.S2C.newBuilder();
        builder.setRs(rs);
        if (rs >= 0) {
            builder.setX(x);
            builder.setY(y);
        }
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        if (itemProxy.getItemId(ItemDefine.MOVE_ITEM_ID) > 0) {
            itemProxy.reduceItemNum(ItemDefine.MOVE_ITEM_ID, 1, LogDefine.LOST_MOVE_CITY);
            Common.ItemInfo info = itemProxy.getItemInfo(ItemDefine.MOVE_ITEM_ID);
            M2.M20007.S2C.Builder itemBuildier = M2.M20007.S2C.newBuilder();
            itemBuildier.addItemList(info);
            pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20007, itemBuildier.build());
        } else {
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            playerProxy.reducePowerValue(PlayerPowerDefine.POWER_gold, ActorDefine.MOVE_PRICE, LogDefine.LOST_MOVE_CITY);
        }
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80005, builder.build());
        if (rs >= 0) {
         /*   BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
            buildingProxy.setWorldBuildingPoint(x,y);*/
            PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
            playerProxy.setWorldTilePoint(x, y);
            M2.M20014.S2C.Builder pointInfo = M2.M20014.S2C.newBuilder();
            pointInfo.setWorldTileX(x).setWorldTileY(y);
            pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20014, pointInfo.build());
            updateMySimplePlayerData();
        }
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        performTasksProxy.clearTeamNotice();
        //TODO 发送80107
        OnTriggerNet80107Event(null);
    }

    private void onStopNodeTeamBack(GameMsg.StopNodeTeamBack mess) {
        List<PlayerTeam> teams = mess.teams();
        HashMap<Integer, Integer> rewardMap = mess.rewardMap();
        HashMap<Integer, Integer> soldierMap = new HashMap<>();
        SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
        for (PlayerTeam team : teams) {
            int typeId = (int) team.getValue(SoldierDefine.NOR_POWER_TYPE_ID);
            int num = (int) team.getValue(SoldierDefine.NOR_POWER_NUM);
            if (soldierMap.containsKey(typeId)) {
                num += soldierMap.get(typeId);
            }
            soldierMap.put(typeId, num);
        }
        for (Integer typeId : soldierMap.keySet()) {
            int num = soldierMap.get(typeId);
            soldierProxy.addSoldierNum(typeId, num, LogDefine.GET_WORLD_FIGHT_TEAM_RETURN);
        }
        soldierProxy.saveSoldier();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        for (Integer key : rewardMap.keySet()) {
            playerProxy.addPowerValue(key, rewardMap.get(key), LogDefine.GET_WORLD_FIGHT_TEAM_RETURN);
        }
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        performTasksProxy.clearPerformTasks();
        performTasksProxy.clearTeamNotice();
    }

//    private void pushTaskListToClient() {
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
//        List<M8.TaskTeamInfo> infos = performTasksProxy.getAllTaskTeamInfoList();
//        M8.M80003.S2C.Builder builder = M8.M80003.S2C.newBuilder();
//        builder.addAllList(infos);
//        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80003, builder.build());
//        GameMsg.RefrshTip msg = new GameMsg.RefrshTip();
//        sendModuleMsg(ActorDefine.ROLE_MODULE_NAME, msg);
//        sendPushNetMsgToClient();
//    }


    private void sendDetectToClient(long id) {
        M8.M80002.S2C.Builder builder = M8.M80002.S2C.newBuilder();
        builder.setRs(0);
        builder.setType(ActorDefine.DETECT_TYPE_DETECT);
        builder.setX(detectX);
        builder.setY(detectY);
        builder.setMailId(id);
        MailProxy mailProxy = getProxy(ActorDefine.MAIL_PROXY_NAME);
        M16.MailDetalInfo.Builder info = M16.MailDetalInfo.newBuilder();
        int rs = mailProxy.getDetalInfo(id, info);
        builder.setReport(info);
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80002, builder.build());
        sendPushNetMsgToClient(0);
    }

    //发送侦查价格到客户端
    private void sendDetectPriceBack(GameMsg.DetectPriceBack mess) {
        M8.M80002.S2C.Builder builder = M8.M80002.S2C.newBuilder();
        if (mess.price() < 0) {
            builder.setRs(mess.price());
        } else {
            builder.setRs(0);
            builder.setPrice(mess.price());
        }
        builder.setType(ActorDefine.DETECT_TYPE_PRICE);
        builder.setX(mess.targetX());
        builder.setY(mess.targetY());
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80002, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80002);
        detectX = mess.targetX();
        detectY = mess.targetY();
        detectPrice = mess.price();
        detectId = mess.id();
    }

    //设置缓存，用于侦查扣费
    private int detectX = -1;
    private int detectY = -1;
    private int detectPrice = -1;
    private long detectId = 0;

    private void sendBuildPointNotify(long id, int x, int y) {
        M2.M20014.S2C.Builder builder = M2.M20014.S2C.newBuilder();
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        Tuple2<Integer, Integer> point = buildingProxy.updateBuildingId(id);
        builder.setWorldTileX(point._1())
                .setWorldTileY(point._2());
        pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20014, builder.build());
        sendPushNetMsgToClient(0);
    }

    private void reduceDefendSoldier(GameMsg.DefendBuildResult resultMess) {
        List<PlayerTeam> teams = resultMess.defendTeams();
        int boomReduce = resultMess.boomReduce();
        int honner = resultMess.honner();
        HashMap<Integer, Integer> rewardMap = resultMess.rewardMap();
        DungeoProxy dungeoProxy = getProxy(ActorDefine.DUNGEO_PROXY_NAME);
        SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
        HashMap<Integer, Integer> deathMap = new HashMap<>();
        List<Integer> ids = dungeoProxy.reduceDeadSoldier(teams, BattleDefine.BATTLE_TYPE_WORLD_DEFEND, deathMap, soldierProxy);
        GameMsg.FixSoldierList msg = new GameMsg.FixSoldierList();
        sendModuleMsg(ActorDefine.SOLDIER_MODULE_NAME, msg);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        //扣除繁荣度
        if (boomReduce > 0) {
            long boomLevel = playerProxy.getPowerValue(PlayerPowerDefine.POWER_boomLevel);
            playerProxy.reducePowerValue(PlayerPowerDefine.POWER_boom, boomReduce, LogDefine.LOST_WORLD_BE_ATTACK);
            long nowBoomLevel = playerProxy.getPowerValue(PlayerPowerDefine.POWER_boomLevel);
            if(boomLevel != nowBoomLevel){
                //推送前端刷新当前界面
                sendRrfrshProtect();
            }
        }
        if (honner > 0) {
            playerProxy.addPowerValue(PlayerPowerDefine.POWER_honour, honner, LogDefine.GET_WORLD);
        } else {
            honner = -honner;
            if (playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour) < honner) {
                honner = (int) playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour);
            }
            playerProxy.reducePowerValue(PlayerPowerDefine.POWER_honour, honner, LogDefine.LOST_WORLD_FIGHT);
        }
        //扣除资源
//        List<Integer> powerList = new ArrayList<>();
        for (Integer key : rewardMap.keySet()) {
            playerProxy.reducePowerValue(key, rewardMap.get(key), LogDefine.LOST_WORLD_BE_ATTACK);
//            powerList.add(key);
        }
//        powerList.add(PlayerPowerDefine.POWER_boom);
//        powerList.add(PlayerPowerDefine.POWER_boomLevel);
//        powerList.add(PlayerPowerDefine.POWER_honour);
//        M2.M20002.S2C dif = sendDifferent(powerList);
//        pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20002, dif);

        //阵型
        FormationProxy formationProxy = getProxy(ActorDefine.FORMATION_PROXY_NAME);
        boolean refurce = formationProxy.checkDefendTroop(soldierProxy, playerProxy.getSettingAutoAddDefendList(), teams, deathMap);
        sendModuleMsg(ActorDefine.TROOP_MODULE_NAME, new GameMsg.SendFormationToClient());
//        if (refurce){
//        }
        M2.M20007.S2C.Builder soldierInfos = M2.M20007.S2C.newBuilder();
        for (Integer id : ids) {
            soldierInfos.addSoldierList(soldierProxy.getSoldierInfo(id));
        }
        pushNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20007, soldierInfos.build());
        //推送到荣耀排行榜
        if (honner > 0) {
            GameMsg.AddPlayerToRank honorRank = new GameMsg.AddPlayerToRank(playerProxy.getPlayerId(),
                    playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour),
                    PowerRanksDefine.POWERRANK_TYPE_HONOR);
            sendServiceMsg(ActorDefine.POWERRANKS_SERVICE_NAME, honorRank);
        }
        playerProxy.getSimplePlayer();
        if (deathMap.size() > 0) {
            sendModuleMsg(ActorDefine.CAPACITY_MODULE_NAME, new GameMsg.CountCapacity());
        }
        playerProxy.allTakeSoldierNum();
        pushNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20500, playerProxy.getBommTimeInfo());

        sendPushNetMsgToClient(0);
    }

    //驻守队伍或者挖掘队伍被攻击的伤兵返还逻辑
    private void addDefendLostSoldiers(List<PlayerTeam> teams,long defendId,int honor) {
        //伤兵返还
        DungeoProxy dungeoProxy = getProxy(ActorDefine.DUNGEO_PROXY_NAME);
        SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
        PlayerProxy playerProxy=getProxy(ActorDefine.PLAYER_PROXY_NAME);
        HashMap<Integer, Integer> deathMap = new HashMap<>();
        if (honor > 0) {
            playerProxy.addPowerValue(PlayerPowerDefine.POWER_honour, honor, LogDefine.GET_WORLD);
        } else {
            honor = -honor;
            if (playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour) < honor) {
                honor = (int) playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour);
            }
            playerProxy.reducePowerValue(PlayerPowerDefine.POWER_honour, honor, LogDefine.LOST_WORLD_FIGHT);
        }
        List<Integer> ids = dungeoProxy.reduceDeadSoldier(teams, BattleDefine.BATTLE_TYPE_WORLD, deathMap, soldierProxy);
        GameMsg.FixSoldierList msg = new GameMsg.FixSoldierList();
        sendModuleMsg(ActorDefine.SOLDIER_MODULE_NAME, msg);
    }

    private void doTeamBackLogic(WorldTeamData teamData) {
        //给玩家添加资源、剩下的佣兵
        List<PlayerTeam> teams = GameUtils.decodePlayerTeam(teamData.getBasePowerMap(),teamData.getPowerMap(),teamData.getPlayerId());
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        if(teamData.getPlayerId() != playerProxy.getPlayerId()){
            System.err.println("返回玩家的部队居然不是玩家自己的！！！");
            //TODO 写入错误日志

            return;
        }
        PlayerReward playerReward = new PlayerReward();
        HashMap<Integer,Integer> reward = GameUtils.decodeStringToIntegerMap(teamData.getRewardMap());
        //获得资源奖励
        JSONObject jsonObject=ConfigDataProxy.getConfigInfoFindByOneKey(DataDefine.ACTIVE_EFFECT, "conditiontype",ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM);
        JSONObject json=ConfigDataProxy.getConfigInfoFindByOneKey(DataDefine.ACTIVE_DESIGN, "effectID",jsonObject.getInt("effectID"));
        ActivityProxy activityProxy = getProxy(ActorDefine.ACTIVITY_PROXY_NAME);
        if(activityProxy.checkActivityIsOpenbyId(json.getInt("ID"))){
            boolean falg= buildingProxy.checkPiontType(teamData.getStartX(),teamData.getStartY());
            if(falg){
                for(Integer in:reward.keySet()){
                    if(in == PlayerPowerDefine.POWER_tael){
                        activityProxy.addActivityConditionValue(ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM,PlayerPowerDefine.POWER_tael,playerProxy,reward.get(in));
                    }
                    if(in == PlayerPowerDefine.POWER_iron){
                        activityProxy.addActivityConditionValue(ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM,PlayerPowerDefine.POWER_iron,playerProxy,reward.get(in));
                    }
                    if(in == PlayerPowerDefine.POWER_wood){
                        activityProxy.addActivityConditionValue(ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM,PlayerPowerDefine.POWER_wood,playerProxy,reward.get(in));
                    }
                    if(in == PlayerPowerDefine.POWER_stones){
                        activityProxy.addActivityConditionValue(ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM,PlayerPowerDefine.POWER_stones,playerProxy,reward.get(in));
                    }
                    if(in == PlayerPowerDefine.POWER_food){
                        activityProxy.addActivityConditionValue(ActivityDefine.ACTIVITY_CONDITION_TYPE_RESOUCE_GETNUM,PlayerPowerDefine.POWER_food,playerProxy,reward.get(in));
                    }
                }

            }

        }
        RewardProxy rewardProxy = getProxy(ActorDefine.REWARD_PROXY_NAME);
        playerReward.addPowerMap.putAll(reward);
        rewardProxy.getRewardToPlayer(playerReward,LogDefine.GET_WORLD_FIGHT_TEAM_RETURN);
        //剩余佣兵返还
        SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
        for (PlayerTeam team : teams) {
            int num = (int) team.getValue(SoldierDefine.NOR_POWER_NUM);
            if (num > 0) {
                int typeId = (int) team.getValue(SoldierDefine.NOR_POWER_TYPE_ID);
                soldierProxy.addSoldierNumWithoutBaseNum(typeId, num, LogDefine.GET_WORLD_FIGHT_TEAM_RETURN);
                playerReward.soldierMap.put(typeId, num);
            }
        }
        if(playerReward.haveReward()){
            pushNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20007, rewardProxy.getRewardClientInfo(playerReward));
        }
        sendPushNetMsgToClient(0);
    }

    private void addLostSoldiers(GameMsg.FightBuildResult mess) {
        List<PlayerTeam> teams = mess.attackTeams();
        int boom = mess.boomAdd();
        int honner = mess.honner();
        boolean result = mess.result();
        PlayerReward reward = mess.playerReward();
        //荣誉值，繁荣度计算
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        playerProxy.addPowerValue(PlayerPowerDefine.POWER_boom, boom, LogDefine.GET_WORLD_FIGHT_TEAM_RETURN);
        if (honner > 0) {
            if (playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour) < honner) {
                honner = (int) playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour);
            }
            playerProxy.addPowerValue(PlayerPowerDefine.POWER_honour, honner, LogDefine.GET_WORLD);
        } else {
            honner = -honner;
            if (playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour) < honner) {
                honner = (int) playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour);
            }
            playerProxy.reducePowerValue(PlayerPowerDefine.POWER_honour, honner, LogDefine.LOST_WORLD_FIGHT);
        }
//        List<Integer> powerList = new ArrayList<>();
//        powerList.add(PlayerPowerDefine.POWER_honour);
//        powerList.add(PlayerPowerDefine.POWER_boom);

        //野怪掉落
        RewardProxy rewardProxy = getProxy(ActorDefine.REWARD_PROXY_NAME);
        rewardProxy.getRewardToPlayer(reward,LogDefine.GET_WORLD);

        //伤兵返还
        DungeoProxy dungeoProxy = getProxy(ActorDefine.DUNGEO_PROXY_NAME);
        SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
        HashMap<Integer, Integer> deathMap = new HashMap<>();
        List<Integer> ids = dungeoProxy.reduceDeadSoldier(teams, BattleDefine.BATTLE_TYPE_WORLD, deathMap, soldierProxy);
        if (result == false) {
            for (PlayerTeam team : teams) {
                int num = (int) team.getValue(SoldierDefine.NOR_POWER_NUM);
                if (num > 0) {
                    int typeId = (int) team.getValue(SoldierDefine.NOR_POWER_TYPE_ID);
                    soldierProxy.addSoldierNumWithoutBaseNum(typeId, num, LogDefine.GET_WORLD_ATTACK_FAIL);
                    reward.soldierMap.put(typeId, num);
                }
            }
            if (reward.soldierMap.size() > 0) {

                pushNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20007, rewardProxy.getRewardClientInfo(reward));
            }
//            deleteTaskNotify(mess.attackTeamId());
        }else{
            //推送前端刷新当前界面
            sendRrfrshProtect();
        }
        if (deathMap.size() > 0) {
            sendModuleMsg(ActorDefine.CAPACITY_MODULE_NAME, new GameMsg.CountCapacity());
        }

        GameMsg.FixSoldierList msg = new GameMsg.FixSoldierList();
        sendModuleMsg(ActorDefine.SOLDIER_MODULE_NAME, msg);

//        M2.M20002.S2C dif = sendDifferent(powerList);
//        pushNetMsg(ActorDefine.ROLE_MODULE_ID, ProtocolModuleDefine.NET_M2_C20002, dif);
        sendPushNetMsgToClient(0);
        //推送到荣耀排行榜
        if (honner > 0) {
            GameMsg.AddPlayerToRank honorRank = new GameMsg.AddPlayerToRank(playerProxy.getPlayerId(),
                    playerProxy.getPowerValue(PlayerPowerDefine.POWER_honour),
                    PowerRanksDefine.POWERRANK_TYPE_HONOR);
            sendServiceMsg(ActorDefine.POWERRANKS_SERVICE_NAME, honorRank);
        }
    }


    private void onWatchWatchmagnifyingback(GameMsg.Watchmagnifyingback info) {
        List<WorldTile> list = info.list();
        int x = info.x();
        int y = info.y();
        BuildingProxy proxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        M8.M80015.S2C.Builder builder = M8.M80015.S2C.newBuilder();
        int rs = proxy.getMagifyInfo(list, x, y, builder);
        builder.setRs(rs);
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80015, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80015);
    }

    private void onWatchBuildingTileInfoBack(GameMsg.WatchBuildingTileInfoBack info) {
        List<WorldTile> list = info.list();

        //打包，发送到客户端

        BuildingProxy proxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        List<M8.WorldTileInfo> infos = new ArrayList<>();
        list.forEach(tile -> infos.add(proxy.getWorldTileInfo(tile)));

        M8.M80000.S2C s2c = M8.M80000.S2C.newBuilder().setRs(0)
                .addAllWorldTileInfos(infos).build();

        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80000, s2c);
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80000);
    }

    //查看坐标周围的格子信息
    private void OnTriggerNet80000Event(Request request) {
        M8.M80000.C2S c2s = request.getValue();
        int rs = 0;
        M8.M80000.S2C.Builder builder = M8.M80000.S2C.newBuilder();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        boolean open = playerProxy.checkeOpenLevel(ActorDefine.OPEN_MAP_ID);
        if (!open) {
            rs = ErrorCodeDefine.M80000_1;
            builder.setRs(rs);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80000, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80000);
        } else {
            //这里做一些功能校验
            //查看相关格子信息
            int x = c2s.getX();
            int y = c2s.getY();
            sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.WatchBuildingTileInfo(x, y));
        }
    }

    private void OnTriggerNet80015Event(Request request) {
        M8.M80015.C2S c2s = request.getValue();
        int x = c2s.getX();
        int y = c2s.getY();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.Watchmagnifying(x, y, playerProxy.getPlayerId()));
    }

    private void OnTriggerNet80001Event(Request request) {
        M8.M80001.C2S c2s = request.getValue();
        int x = c2s.getX();
        int y = c2s.getY();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        List<Common.FightElementInfo> list = c2s.getTeamList();
        BattleProxy battleProxy = getProxy(ActorDefine.BATTLE_PROXY_NAME);
        int rs =0;
        List<JSONObject> openObject = ConfigDataProxy.getConfigInfoFilterById(DataDefine.FUNCTION_OPEN, ActorDefine.OPEN_MAP_ID);
        for (JSONObject ls : openObject) {
            if (!(playerProxy.getLevel() >= ls.getInt("openlevel"))) {
                rs = ErrorCodeDefine.M80001_15;
                break;
            }
        }
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        rs = buildingProxy.fightWorld(x,y,list);
        Tuple2<Integer, Integer> point = buildingProxy.getWorldTilePoint();

        M8.M80001.S2C.Builder builder = M8.M80001.S2C.newBuilder();
        builder.setRs(rs);
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80001, builder.build());
        sendFuntctionLog(FunctionIdDefine.GET_WORLD_TILE_INFO_FUNCTION_ID);
        // playerProxy.reducePowerValue(PlayerPowerDefine.POWER_energy, 1, LogDefine.LOST_WORLD_FIGHT);
        if(rs>=0) {
            List<PlayerTeam> teams = battleProxy.createFightTeamList(list);
            ActivityProxy activityProxy = getProxy(ActorDefine.ACTIVITY_PROXY_NAME);
            Map<Integer, Integer> powerMap = new HashMap<>();
            powerMap.put(PlayerPowerDefine.NOR_POWER_resexprate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_resexprate));
            powerMap.put(PlayerPowerDefine.NOR_POWER_rescollectrate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_rescollectrate));
            powerMap.put(PlayerPowerDefine.NOR_POWER_speedRate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_speedRate));
            powerMap.put(PlayerPowerDefine.NOR_POWER_loadRate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_loadRate));
            powerMap.put(ActivityDefine.ACTIVITY_CONDITION_FIGHT_WORLD_REWARD_RATE, (int) activityProxy.getExpandPowerValue(ActivityDefine.ACTIVITY_CONDITION_FIGHT_WORLD_REWARD_RATE));
            WorldTeamData worldTeamData = buildingProxy.createTeamData(x, y, teams, TaskDefine.PERFORM_TASK_ATTACK, (HashMap<Integer, Integer>) powerMap);

            WorldNodeData targetNode = WorldService.getWorldNode(playerProxy.getAreaKey(), x, y);
            targetNode.getFightList().add(worldTeamData.getId());

            GameMsg.FightBuild mess = new GameMsg.FightBuild(worldTeamData, powerMap);
            WorldService.addTeamData(playerProxy.getAreaKey(), worldTeamData);

            if (targetNode.getOccupyPlayerId() > 0) {
                //通知其他玩家被攻击了
                SimplePlayer targetSimplePlayer = PlayerService.getSimplePlayer(targetNode.getOccupyPlayerId(), playerProxy.getAreaKey());
                GameMsg.TeamDataAdd teamDataAdd = new GameMsg.TeamDataAdd(worldTeamData.getId());
                if (targetSimplePlayer.online) {
                    sendMsgToOtherPlayerModule(ActorDefine.MAP_MODULE_NAME, targetSimplePlayer.getAccountName(), teamDataAdd);
                }

                //自己如果有保护罩，要清空保护罩
                if (playerProxy.getProtectOverDate() > GameUtils.getServerDate().getTime()) {
                    playerProxy.setProtectOverDate(0);
                    ItemBuffProxy itemBuffProxy = getProxy(ActorDefine.ITEMBUFF_PROXY_NAME);
                    itemBuffProxy.delBuffer(PlayerPowerDefine.NOR_POWER_protect_date);
                    playerProxy.getSimplePlayer();
                    sendRrfrshProtect();
                }
            }
            //发送到世界服务
            sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, mess);


            //扣除出战的佣兵
            SoldierProxy soldierProxy = getProxy(ActorDefine.SOLDIER_PROXY_NAME);
            HashMap<Integer, Integer> reduceMap = new HashMap<>(6);
            for (Common.FightElementInfo info : list) {
                if (reduceMap.containsKey(info.getTypeid())) {
                    int num = reduceMap.get(info.getTypeid()) + info.getNum();
                    reduceMap.put(info.getTypeid(), num);
                } else {
                    reduceMap.put(info.getTypeid(), info.getNum());
                }
            }
            List<Common.SoldierInfo> infos = new ArrayList<>(6);
            for (Integer id : reduceMap.keySet()) {
                soldierProxy.reduceSoldierNumWithoutBaseNum(id, reduceMap.get(id), LogDefine.LOST_WORLD_ATTACK);
                infos.add(soldierProxy.getSoldierInfo(id));
            }
            M2.M20007.S2C.Builder refSoldiers = M2.M20007.S2C.newBuilder();
            refSoldiers.addAllSoldierList(infos);
            sendNetMsg(ProtocolModuleDefine.NET_M2, ProtocolModuleDefine.NET_M2_C20007, refSoldiers.build());
            FormationProxy formationProxy = getProxy(ActorDefine.FORMATION_PROXY_NAME);
            boolean refurce = formationProxy.checkDefendTroop(soldierProxy, ActorDefine.SETTING_AUTO_ADD_DEFEND_TEAM_ON, null, null);
            if (refurce) {
                //刷新防守队伍的playerTeam
                sendModuleMsg(ActorDefine.TROOP_MODULE_NAME, new GameMsg.SendFormationToClient());
            }
            TaskProxy taskProxy = getProxy(ActorDefine.TASK_PROXY_NAME);
            PlayerReward reward = new PlayerReward();
            taskProxy.getTaskUpdate(TaskDefine.TASK_TYPE_CREATESODIER_NUM, 0);
            sendFuntctionLog(FunctionIdDefine.FIGHT_WORLD_MAP_FUNCTION_ID, x, y, 0);

            // 通知玩家增加了进攻队列
            updateTaskNotify(worldTeamData.getId());
        }
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80001);
    }



    private void OnTriggerNet80002Event(Request request) {
        M8.M80002.C2S c2s = request.getValue();
        int x = c2s.getX();
        int y = c2s.getY();
        int type = c2s.getType();
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        Tuple2<Integer, Integer> point = buildingProxy.getWorldTilePoint();
        if (point._1() == x && point._2() == y) {
            M8.M80002.S2C.Builder builder = M8.M80002.S2C.newBuilder();
            builder.setRs(ErrorCodeDefine.M80002_4);
            builder.setType(type);
            builder.setX(x).setY(y);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80002, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80002);
            return;
        }
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        if (type == ActorDefine.DETECT_TYPE_DETECT) {
            if (x != detectX || y != detectY) {
                M8.M80002.S2C.Builder builder = M8.M80002.S2C.newBuilder();
                builder.setRs(ErrorCodeDefine.M80002_2);
                builder.setType(type);
                builder.setX(x).setY(y);
                sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80002, builder.build());
                sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80002);
                return;
            }
            if (detectPrice > playerProxy.getPowerValue(PlayerPowerDefine.POWER_tael)) {
                M8.M80002.S2C.Builder builder = M8.M80002.S2C.newBuilder();
                builder.setRs(ErrorCodeDefine.M80002_3);
                builder.setType(type);
                builder.setX(x).setY(y);
                sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80002, builder.build());
                sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80002);
                return;
            } else {
                playerProxy.reducePowerValue(PlayerPowerDefine.POWER_tael, detectPrice, LogDefine.LOST_DETECT);
                sendFuntctionLog(FunctionIdDefine.DETECT_WORLD_MAP_FUNCTION_ID, x, y, detectId, detectPrice + "");
            }
        }
        sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.DetectBuild(x, y, type, playerProxy.getPlayerId()));
    }


//    private void OnTriggerNet80003Event(Request request) {
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
//        List<M8.TaskTeamInfo> infos = performTasksProxy.getAllTaskTeamInfoList();
//        M8.M80003.S2C.Builder builder = M8.M80003.S2C.newBuilder();
//        builder.addAllList(infos);
//        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80003, builder.build());
//        sendFuntctionLog(FunctionIdDefine.GET_ALL_TASK_TEAM_INFO_LIST_FUNCTION_ID);
//        sendPushNetMsgToClient();
//    }

    private void OnTriggerNet80004Event(Request request) {
        M8.M80004.C2S c2s = request.getValue();
        long id = c2s.getId();
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        WorldTeamData teamData = WorldService.getTeamData(id);
        int rs = buildingProxy.buyQuickFinishTaskTeam(teamData);
//        int rs = performTasksProxy.buyQuickFinishPerformTask(id, point);
        M8.M80004.S2C.Builder builder = M8.M80004.S2C.newBuilder();
        builder.setRs(rs);
        builder.setId(id);
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80004, builder.build());
        if (rs >= 0) {
            if (teamData.getType() == TaskDefine.PERFORM_TASK_DIGGING || teamData.getType() == TaskDefine.PERFORM_TASK_HELPBACK){
                GameMsg.CallBackTask mess = new GameMsg.CallBackTask(id);
                tellMsgToWorldNode(mess, teamData.getTargetX(), teamData.getTargetY());
            }else{
                teamData.setEndTime(0);
            }
            sendFuntctionLog(FunctionIdDefine.BUY_QUICK_FINISH_PERFORM_TASK_FUNCTION_ID, teamData.getTargetX(), teamData.getTargetY(), id);
        }
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80004);
    }

    private void OnTriggerNet80005Event(Request request) {
        M8.M80005.C2S c2s = request.getValue();
        int x = c2s.getX();
        int y = c2s.getY();
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        int num = itemProxy.getItemNum(ItemDefine.MOVE_ITEM_ID);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        BuildingProxy buildingProxy=getGameProxy().getProxy(ActorDefine.BUILDING_PROXY_NAME);
        if (num <= 0 && playerProxy.getPowerValue(PlayerPowerDefine.POWER_gold) < ActorDefine.MOVE_PRICE) {
            M8.M80005.S2C.Builder builder = M8.M80005.S2C.newBuilder();
            builder.setRs(ErrorCodeDefine.M80005_1);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80005, builder.build());
        } else if (buildingProxy.getTaskNum() > 0) {
            M8.M80005.S2C.Builder builder = M8.M80005.S2C.newBuilder();
            builder.setRs(ErrorCodeDefine.M80005_3);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80005, builder.build());
        } else {
            Tuple2<Integer, Integer> point = buildingProxy.getWorldTilePoint();
            sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.MoveWorldBuild(x, y, point._1(), point._2()));
        }
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80005);
    }

    private void OnTriggerNet80006Event(Request request) {
        M8.M80006.C2S c2s = request.getValue();
        String name = c2s.getName();
        int typeId = c2s.getTypeId();
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        int rs = itemProxy.getPointItem(typeId);
        if (rs <= 0) {
            M8.M80006.S2C.Builder builder = M8.M80006.S2C.newBuilder();
            builder.setRs(rs);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80006, builder.build());
            sendFuntctionLog(FunctionIdDefine.FIND_PLAYER_COORDS_FUNCTION_ID, 0, 0, 0, name);
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80006);
            return;
        }
        itemProxy.pointItem = typeId;
        sendServiceMsg(ActorDefine.PLAYER_SERVICE_NAME, new GameMsg.GetPlayerSimpleInfoByRoleName(name, "80006"));
    }

//    private void OnTriggerNet80007Event(Request request) {
//        M8.M80007.S2C.Builder builder = M8.M80007.S2C.newBuilder();
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
//        List<M8.TeamNoticeInfo> list = performTasksProxy.getAllTeamNoticeInfo();
//        builder.addAllInfos(list);
//        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80007, builder.build());
//        sendFuntctionLog(FunctionIdDefine.GET_ALL_TEAM_NOTICE_INFO_FUNCTION_ID);
//        sendPushNetMsgToClient();
//    }


    private void OnTriggerNet80008Event(Request request) {
        M8.M80008.C2S c2S = request.getValue();
        M8.CollectInfo collectInfo = c2S.getColinfo();
        GameMsg.CollectMsg msg = new GameMsg.CollectMsg(collectInfo);
        sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, msg);

    }

    private void OnTriggerNet80009Event(Request request) {
        M8.M80009.C2S c2S = request.getValue();
        M8.M80009.S2C.Builder builder = M8.M80009.S2C.newBuilder();
        long id = c2S.getId();
        CollectProxy collectProxy = getProxy(ActorDefine.COLLECT_PROXY_NAME);
        collectProxy.removeCol(id);
        builder.addAllInfos(collectProxy.getCollectInfos());
        builder.setRs(0);
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80009, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80009);
    }

    private void OnTriggerNet80010Event(Request request) {
        M8.M80010.S2C.Builder builder = M8.M80010.S2C.newBuilder();
        builder.setRs(0);
        CollectProxy collectProxy = getProxy(ActorDefine.COLLECT_PROXY_NAME);
        builder.addAllInfos(collectProxy.getCollectInfos());
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80010, builder.build());
        sendFuntctionLog(FunctionIdDefine.CHECK_COLLECT_INFO_FUNCTION_ID);
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80010);
    }

    private void OnTriggerNet80011Event(Request request) {
        ItemProxy itemProxy = getProxy(ActorDefine.ITEM_PROXY_NAME);
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        if (itemProxy.getItemNum(ItemDefine.MOVE_RANDOM_ITEM_ID) <= 0) {
            M8.M80011.S2C.Builder builder = M8.M80011.S2C.newBuilder();
            builder.setRs(ErrorCodeDefine.M80011_1);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80011, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80011);
            return;
        } else if (performTasksProxy.getTaskNum() > 0) {
            M8.M80011.S2C.Builder builder = M8.M80011.S2C.newBuilder();
            builder.setRs(ErrorCodeDefine.M80011_2);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80011, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80011);
            return;
        }
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        Tuple2<Integer, Integer> tuple = buildingProxy.getWorldTilePoint();
        sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.MoveRandomWorldBuild(playerProxy.getPlayerId(), tuple._1(), tuple._2()));
    }

    private void OnTriggerNet80012Event(Request request) {
        M8.M80012.C2S c2S = request.getValue();
        int x = c2S.getX();
        int y = c2S.getY();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        int rs = 0;
        if (playerProxy.getArmGrouId() <= 0) {
            rs = ErrorCodeDefine.M80012_1;
        }
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        Tuple2<Integer, Integer> tuple = buildingProxy.getWorldTilePoint();
        if (tuple._1() == x && tuple._2() == y) {
            rs = ErrorCodeDefine.M80012_2;
        }
        Map<Integer, Long> powerMap = new HashMap<Integer, Long>();
        powerMap.put(PlayerPowerDefine.NOR_POWER_resexprate, playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_resexprate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_rescollectrate, playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_rescollectrate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_speedRate, playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_speedRate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_loadRate, playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_loadRate));
        rs = buildingProxy.helpDefendBuilding(x,y,null);
        int time = 0;
        if(rs >= 0){
            int endTime = WorldService.getTheWayTime(tuple._1(), tuple._2(), x, y, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_speedRate));
            time = endTime - GameUtils.getServerTime();
        }
        M8.M80012.S2C.Builder builder = M8.M80012.S2C.newBuilder();
        builder.setRs(rs);
        builder.setTiem(time);
        builder.setX(x);
        builder.setY(y);
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80012, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80012);
    }


    private void OnTriggerNet80013Event(Request request) {
        M8.M80013.C2S c2S = request.getValue();
        int x = c2S.getX();
        int y = c2S.getY();
        List<Common.FightElementInfo> list = c2S.getTeamList();
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        int rs = buildingProxy.helpDefendBuilding(x,y,list);
        M8.M80013.S2C.Builder builder = M8.M80013.S2C.newBuilder();
        builder.setRs(rs);
        if (rs < 0) {
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80013, builder.build());
            sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80013);
            return;
        }
        Map<Integer, Integer> powerMap = new HashMap<>();
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        BattleProxy battleProxy = getProxy(ActorDefine.BATTLE_PROXY_NAME);
        List<PlayerTeam> fightteam = battleProxy.createFightTeamList(list);
        Tuple2<Integer, Integer> tuple = buildingProxy.getWorldTilePoint();
        powerMap.put(PlayerPowerDefine.NOR_POWER_resexprate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_resexprate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_rescollectrate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_rescollectrate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_speedRate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_speedRate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_loadRate, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_loadRate));
        powerMap.put(PlayerPowerDefine.NOR_POWER_load, (int) playerProxy.getPowerValue(PlayerPowerDefine.NOR_POWER_load));
        List<Integer> openPost = playerProxy.getPlayerFightPost();
        powerMap.put(PlayerPowerDefine.POWER_command, (int) (playerProxy.getPowerValue(PlayerPowerDefine.POWER_command) * openPost.size()));
        WorldTeamData teamData = buildingProxy.createTeamData(x,y,fightteam,TaskDefine.PERFORM_TASK_GOHELP, (HashMap<Integer, Integer>) powerMap);
        WorldService.addTeamData(playerProxy.getAreaKey(),teamData);
        sendServiceMsg(ActorDefine.WORLD_SERVICE_NAME, new GameMsg.Tohelp(teamData, powerMap));
        //通知其他玩家增加驻防队列
        WorldTile targetTile = WorldService.getWorldTitleByPoint(x,y,playerProxy.getAreaKey());
        WorldNodeData worldNodeData= WorldService.getWorldNode(playerProxy.getAreaKey(),x,y);
        worldNodeData.getHelplist().add(teamData.getId());
        if(targetTile.tileType() == TileType.Building()){
            //通知其他玩家被驻防了
            SimplePlayer targetSimplePlayer = PlayerService.getSimplePlayer(targetTile.building().getPlayerId(),playerProxy.getAreaKey());
            GameMsg.TeamDataAdd teamDataAdd = new GameMsg.TeamDataAdd(teamData.getId());
            if(targetSimplePlayer.online){
                sendMsgToOtherPlayerModule(ActorDefine.MAP_MODULE_NAME,targetSimplePlayer.getAccountName(),teamDataAdd);
            }
        }
        //通知玩家增加了驻防队列
        updateTaskNotify(teamData.getId());
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80013, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80013);
    }

    private void OnTriggerNet80014Event(Request request) {
        M8.M80014.C2S c2S = request.getValue();
        long id = c2S.getId();
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        int rs = performTasksProxy.changerHelp(id);
        M8.M80014.S2C.Builder builder = M8.M80014.S2C.newBuilder();
        builder.setRs(rs);
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80014, builder.build());
        if (rs == 0) {
            updateMySimplePlayerData();
        }
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80014);
    }

    private void OnTriggerNet80103Event(Request request) {
        M8.M80103.C2S c2S = request.getValue();
        long taskId = c2S.getId();
        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        int rs = performTasksProxy.checkIsTimeOut(taskId);
        M8.M80103.S2C.Builder builder = M8.M80103.S2C.newBuilder();
        if (rs == 0) {
            builder.setRs(rs);
            builder.setId(taskId);
        } else {
            builder.setRs(rs);
            builder.setId(taskId);
            PerformTasks tasks = performTasksProxy.getTaskById(taskId);
            long totalTime = (GameUtils.getServerDate().getTime());
            long timmer = tasks.getTimeer();
            int alreadyTime =(int) ((timmer - totalTime) / 1000);
            builder.setAlreadyTime(alreadyTime);
        }
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80103, builder.build());
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80103);
    }

//    private void pushAddTaskInfo(long taskId) {
//        PerformTasksProxy performTasksProxy = getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
//        M8.M80104.S2C.Builder builder = M8.M80104.S2C.newBuilder();
//        builder.setRs(0);
//        M8.TaskTeamInfo taskTeamInfo = performTasksProxy.getTaskTeamInfoById(taskId);
//        builder.setTaskTeamInfo(taskTeamInfo);
//        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80104, builder.build());
//        sendPushNetMsgToClient(0);
//    }

//    private void pushTaskInfo(long taskId) {
//        M8.M80103.S2C.Builder builder80103 = M8.M80103.S2C.newBuilder();
//        builder80103.setRs(0);
//        builder80103.setId(taskId);
//        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80103, builder80103.build());
//        sendPushNetMsgToClient(0);
//    }

    private void OnTriggerNet80107Event(Request request) {
        M8.M80107.S2C.Builder builder = M8.M80107.S2C.newBuilder();
        if (request == null) {
            builder.setRs(1);
            builder.setKey(0);
        } else {
            M8.M80107.C2S c2S = request.getValue();
            long key = c2S.getKey();
            PerformTasksProxy performTasksProxy = getGameProxy().getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
            int rs = performTasksProxy.checkTeamNoticeTimeOut(key);
            if (rs == 0) {
                builder.setRs(rs);
                builder.setKey(key);
            } else {
                builder.setRs(rs);
                builder.setKey(key);
                TeamNotice teamNotice = performTasksProxy.getTeamNoticeByKey(key);
                int now = (int) (GameUtils.getServerDate().getTime() / 1000);
                int arriveTime = (int) (teamNotice.getArriveTime() / 1000);
                int remainTime = arriveTime - now;
                builder.setTime(remainTime);
            }
        }
        sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80107, builder.build());
        sendFuntctionLog(FunctionIdDefine.GET_ALL_TEAM_NOTICE_INFO_FUNCTION_ID);
        sendPushNetMsgToClient(ProtocolModuleDefine.NET_M8_C80107);

    }



    private void pushTeamNoticeToClient(long key) {
        M8.M80108.S2C.Builder builder = M8.M80108.S2C.newBuilder();
        PerformTasksProxy performTasksProxy = getGameProxy().getProxy(ActorDefine.PERFORMTASKS_PROXY_NAME);
        builder.setRs(0);
        TeamNotice teamNotice = performTasksProxy.getTeamNoticeByKey(key);
        M8.TeamNoticeInfo teamNoticeInfo = performTasksProxy.getTeamNoticeInfo(teamNotice);
        builder.addInfos(teamNoticeInfo);
        pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80108, builder.build());
        sendPushNetMsgToClient(0);
    }

    private void tellMsgToWorldNode(Object mess, int x, int y) {
        context().actorSelection("../../../" + ActorDefine.WORLD_SERVICE_NAME + "/" + x + "_" + y).tell(mess, self());
    }

    private void tellMsgToArmygroupNode(Object mess, Long id) {
        context().actorSelection("../../../" + ActorDefine.ARMYGROUP_SERVICE_NAME + "/" + ActorDefine.ARMYGROUPNODE + id).tell(mess, self());
    }

    /**
     * 重复协议请求处理
     * @param request
     */
    @Override
    public void repeatedProtocalHandler(Request request) {

    }



    //推送队伍删除信息给前端
    private void deleteTaskNotify(WorldTeamData teamData){
        long id = teamData.getId();
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        if(buildingProxy.taskSet.contains(id)){
            M8.M80103.S2C.Builder builder = M8.M80103.S2C.newBuilder();
            builder.setId(id);
            builder.setRs(0);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80103, builder.build());
            buildingProxy.taskSet.remove(id);
            if(teamData.getType() == TaskDefine.PERFORM_TASK_DIGGING){
                PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
                long key = (long) (teamData.getTargetX()*1000+teamData.getTargetY());
                playerProxy.getPlayer().removeWorldResPoint(key);
            }
        }
        if(buildingProxy.noticeSet.contains(id)){
            M8.M80107.S2C.Builder builder = M8.M80107.S2C.newBuilder();
            builder.setRs(0);
            builder.setKey(id);
            sendNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80107, builder.build());
            buildingProxy.noticeSet.remove(id);
        }
        sendPushNetMsgToClient(0);
    }

    private void updateTaskNotify(long teamId){
        WorldTeamData teamData = WorldService.getTeamData(teamId);
        PlayerProxy playerProxy = getProxy(ActorDefine.PLAYER_PROXY_NAME);
        BuildingProxy buildingProxy = getProxy(ActorDefine.BUILDING_PROXY_NAME);
        if(teamData.getPlayerId() != playerProxy.getPlayerId() && teamData.getType() == TaskDefine.PERFORM_TASK_ATTACK){
            //被攻击提示，只产生notify
            M8.M80108.S2C.Builder builder = M8.M80108.S2C.newBuilder();
            builder.setRs(0);
            builder.addInfos(buildingProxy.getBeAttackTeamNoticeInfo(teamData));
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80108, builder.build());
            buildingProxy.noticeSet.add(teamId);
        }else{
            M8.M80104.S2C.Builder builder = M8.M80104.S2C.newBuilder();
            builder.setRs(0);
            builder.setTaskTeamInfo(buildingProxy.getTaskTeamInfo(teamData));
            pushNetMsg(ProtocolModuleDefine.NET_M8, ProtocolModuleDefine.NET_M8_C80104, builder.build());
            buildingProxy.taskSet.add(teamId);
            long key = (long) (teamData.getTargetX()*1000+teamData.getTargetY());
            if (teamData.getType() == TaskDefine.PERFORM_TASK_DIGGING){
                playerProxy.getPlayer().addtWorldResPoint(key);//保存在玩家的坐标以x*1000+y的形式
            }else if(playerProxy.getPlayer().getWorldResPoint().contains(key)){
                playerProxy.getPlayer().removeWorldResPoint(key);
            }
        }
        sendPushNetMsgToClient(0);
    }
}
