package com.znl.node.battle.data.buffData;

import java.util.ArrayList;
import java.util.List;

import com.znl.define.SoldierDefine;
import com.znl.node.battle.buff.Buff;
import com.znl.node.battle.consts.BattleConst.BuffTickType;
import com.znl.node.battle.consts.BattleConst.BuffType;
import com.znl.node.battle.entity.PuppetEntity;

public class Buff1101 extends Buff {

    public Buff1101 (PuppetEntity role) {
       super(role);//增加己方所有部队15%闪避

       super.id = 1101;
       super.iconId = 0;
       super.lastRound = 100;
       tickType = BuffTickType.RoundEnd;
}

   @Override
   public void onOccur() {
           int value = role.getAttrValue(SoldierDefine.POWER_dodgeRate);
           super.modifyAttr(SoldierDefine.POWER_dodgeRate, value+1500, false, 0);
           super.onOccur();}

   @Override
   public void onRoundTick() {
           
           super.onRoundTick();
   }

 }
