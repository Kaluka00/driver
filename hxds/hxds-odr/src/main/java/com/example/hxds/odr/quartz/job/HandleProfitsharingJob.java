package com.example.hxds.odr.quartz.job;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPay;
import com.example.hxds.common.wxpay.WXPayConstants;
import com.example.hxds.common.wxpay.WXPayUtil;
import com.example.hxds.odr.db.dao.OrderProfitsharingDao;
import com.example.hxds.odr.quartz.QuartzUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;

import javax.annotation.Resource;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HandleProfitsharingJob extends QuartzJobBean {
    @Resource
    private OrderProfitsharingDao profitsharingDao;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Resource
    private QuartzUtil quartzUtil;


    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Map map = context.getJobDetail().getJobDataMap();
        String uuid = MapUtil.getStr(map, "uuid");
        String driverOpenId = MapUtil.getStr(map, "driverOpenId");
        String payId = MapUtil.getStr(map, "payId");

        map=profitsharingDao.searchDriverIncome(uuid);
        if(map==null||map.size()==0){
            log.error("没有查询到分账记录");
            return;
        }
        String driverIncome = MapUtil.getStr(map, "driverIncome");
        long profitsharingId = MapUtil.getLong(map, "profitsharingId");

        try{
            WXPay wxPay=new WXPay(myWXPayConfig);
            HashMap param=new HashMap(){{
                put("appid", myWXPayConfig.getAppID());
                put("mch_id", myWXPayConfig.getMchID());
                put("nonce_str", WXPayUtil.generateNonceStr());
                put("out_order_no", uuid);
                put("transaction_id", payId);
            }};

            JSONArray receivers = new JSONArray();
            JSONObject json=new JSONObject();
            json.set("type", "PERSONAL_OPENID");
            json.set("account", driverOpenId);
            //分账金额从元转换成分
            int amount = Integer.parseInt(NumberUtil.mul(driverIncome, "100").setScale(0, RoundingMode.FLOOR).toString());
//            json.set("amount", amount);
            json.set("amount", 1); //设置分账金额为1分钱（测试阶段）
            json.set("description", "给司机的分账");
            receivers.add(json);
            param.put("receivers", receivers.toString());
            String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256);
            param.put("sign",sign);

            String url = "/secapi/pay/profitsharing";
            String response = wxPay.requestWithCert(url, param, 3000, 3000);
//            log.debug(response);
            System.out.println(response);
            if(WXPayUtil.isSignatureValid(response,myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256)){
                Map<String, String> data = wxPay.processResponseXml(response, WXPayConstants.SignType.HMACSHA256);
                String returnCode = data.get("return_code");
                String resultCode = data.get("result_code");
                if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
                    String status = data.get("status");
                    //判断分账成功
                    if ("FINISHED".equals(status)) {
                        int rows = profitsharingDao.updateProfitsharingStatus(profitsharingId);
                        if(rows != 1){
                            log.error("更新分账状态失败", new HxdsException("更新分账状态失败"));
                        }
                    }
                    //判断正在分账中
                    else if ("PROCESSING".equals(status)) {
                        //TODO 创建查询分账定时器
                        System.out.println("分账中");
                        JobDetail jobDetail = JobBuilder.newJob(SearchProfitsharingJob.class).build();
                        Map dataMap = jobDetail.getJobDataMap();
                        dataMap.put("uuid", uuid);
                        dataMap.put("profitsharingId", profitsharingId);
                        dataMap.put("payId", payId);

                        DateTime executeDate = new DateTime().offset(DateField.MINUTE, 20);
                        quartzUtil.addJob(jobDetail,uuid,"查询代驾单分账任务组",executeDate);
                    }
                }
                else{
                    log.error("执行分账失败", new HxdsException("执行分账失败"));
                }
            }
            else {
                log.error("验证数字签名失败", new HxdsException("验证数字签名失败"));
            }
        }catch (Exception e){
            log.error("执行分账失败", e);
        }
    }
}
