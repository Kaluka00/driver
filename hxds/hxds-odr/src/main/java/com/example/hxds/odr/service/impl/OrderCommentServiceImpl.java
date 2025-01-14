package com.example.hxds.odr.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.map.MapUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.odr.db.dao.OrderCommentDao;
import com.example.hxds.odr.db.dao.OrderDao;
import com.example.hxds.odr.db.pojo.OrderCommentEntity;
import com.example.hxds.odr.service.OrderCommentService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ciModel.auditing.AuditingJobsDetail;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingResponse;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class OrderCommentServiceImpl implements OrderCommentService {
    @Value("${tencent.cloud.appId}")
    private String appId;

    @Value("${tencent.cloud.secretId}")
    private String secretId;

    @Value("${tencent.cloud.secretKey}")
    private String secretKey;

    @Value("${tencent.cloud.bucket-public}")
    private String bucketPublic;

    @Resource
    private OrderCommentDao orderCommentDao;

    @Resource
    private OrderDao orderDao;


    @Override
    @Transactional
    @LcnTransaction
    public int insert(OrderCommentEntity entity) {
        HashMap param = new HashMap() {{
            put("orderId", entity.getOrderId());
            put("driverId", entity.getDriverId());
            put("customerId", entity.getCustomerId());
        }};
        long count = orderDao.validDriverAndCustomerOwnOrder(param);
        if (count != 1) {
            throw new HxdsException("司机和乘客与该订单无关联");
        }

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region region = new Region("ap-beijing");
        ClientConfig config = new ClientConfig(region);
        COSClient client = new COSClient(cred, config);
        TextAuditingRequest request = new TextAuditingRequest();
        request.setBucketName(bucketPublic);
        request.getInput().setContent(Base64.encode(entity.getRemark()));
        request.getConf().setDetectType("all");

        TextAuditingResponse response = client.createAuditingTextJobs(request);
        AuditingJobsDetail detail = response.getJobsDetail();
        String state = detail.getState();
        if ("Success".equals(state)) {
            String result = detail.getResult();
            //内容审查不同过就设置评价内容为null
            if (!"0".equals(result)) {
                entity.setRemark(null);
            }
        }
        int rows = orderCommentDao.insert(entity);
        if (rows != 1) {
            throw new HxdsException("保存订单评价失败");
        }
        return rows;
    }

    @Override
    public HashMap searchCommentByOrderId(Map param) {
        HashMap map = orderCommentDao.searchCommentByOrderId(param);
        return map;
    }

    @Override
    public PageUtils searchCommentByPage(Map param) {
        long count = orderCommentDao.searchCommentCount(param);
        ArrayList<HashMap> list=null;
        if(count>0){
            list = orderCommentDao.searchCommentByPage(param);
            list.forEach(one->{
                Integer temp = MapUtil.getInt(one, "handler");
                one.replace("handler",temp==1);
            });
        }
        else{
            list=new ArrayList<>();
        }
        int start = MapUtil.getInt(param, "start");
        int length = MapUtil.getInt(param, "length");
        PageUtils pageUtils=new PageUtils(list,count,start,length);
        return pageUtils;
    }


}
