package com.znl.node.battle.data.buffData;

import java.util.ArrayList;
import java.util.List;

import com.znl.define.SoldierDefine;
import com.znl.node.battle.buff.Buff;
import com.znl.node.battle.consts.BattleConst.BuffTickType;
import com.znl.node.battle.consts.BattleConst.BuffType;
import com.znl.node.battle.entity.PuppetEntity;

public class Buff1161 extends Buff {

    public Buff1161 (PuppetEntity role) {
       super(role);//增加已方火箭10%暴击

       super.id = 1161;
       super.iconId = 0;
       super.lastRound = 100;
       tickType = BuffTickType.RoundEnd;
}

   @Override
   public void onOccur() {
           int value = role.getAttrValue(SoldierDefine.POWER_critRate);
           super.modifyAttr(SoldierDefine.POWER_critRate, value+1000, false, 0);
           super.onOccur();}

   @Override
   public void onRoundTick() {
           
           super.onRoundTick();
   }

 }
