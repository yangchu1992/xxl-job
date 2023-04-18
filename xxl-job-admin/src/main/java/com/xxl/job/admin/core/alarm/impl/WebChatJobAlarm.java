package com.xxl.job.admin.core.alarm.impl;

import com.xxl.job.admin.core.alarm.AlarmTypeEnum;
import com.xxl.job.admin.core.alarm.JobAlarm;
import com.xxl.job.admin.core.alarm.msg.WechatTextMsgReq;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class WebChatJobAlarm implements JobAlarm {

    private static final Logger logger = LoggerFactory.getLogger(WebChatJobAlarm.class);

    private static RestTemplate restTemplate = new RestTemplate();
    private static String br = "\n";

    private String title;

    @Override
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog) {

        if (info != null && ObjectUtils.nullSafeEquals(AlarmTypeEnum.ENT_WECHAT.getAlarmType(), info.getAlarmType()) && info.getAlarmUrl() != null && info.getAlarmUrl().trim().length() > 0) {

            String content = parseContent(info, jobLog);
            WechatTextMsgReq textMsgReq = new WechatTextMsgReq();
            textMsgReq.withContent(content);
            return sendToAll(jobLog, info, textMsgReq.toJson());

        }

        return false;
    }

    public String parseContent(XxlJobInfo info, XxlJobLog jobLog) {

        // alarmContent
        String alarmContent = "Alarm Job LogId=" + jobLog.getId();
        if (jobLog.getTriggerCode() != ReturnT.SUCCESS_CODE) {
            alarmContent += br + "TriggerMsg=" + br + jobLog.getTriggerMsg();
        }
        if (jobLog.getHandleCode() > 0 && jobLog.getHandleCode() != ReturnT.SUCCESS_CODE) {
            alarmContent += br + "HandleCode=" + jobLog.getHandleMsg();
        }
        // email info
        XxlJobGroup group = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().load(Integer.valueOf(info.getJobGroup()));
        //String personal = I18nUtil.getString("admin_name_full");
        title = I18nUtil.getString("jobconf_monitor");
        String content = MessageFormat.format(loadJobAlarmTemplate(),
                group != null ? group.getTitle() : "null",
                info.getId(),
                info.getJobDesc(),
                alarmContent);
        content = content.replaceAll("<br><br>", "\n");
        content = content.replaceAll("<span style=\"color:#00c0ef;\" >", "");
        content = content.replaceAll("</span>", "");
        content = content.replaceAll("<br>", "\n");
        return content.trim();
    }


    /**
     * load webhook job alarm template
     *
     * @return
     */
    private static final String loadJobAlarmTemplate() {
        String mailBodyTemplate = I18nUtil.getString("jobconf_monitor_detail") + br +
                I18nUtil.getString("jobinfo_field_jobgroup") + ":{0}" + br +
                I18nUtil.getString("jobinfo_field_id") + ":{1}" + br +
                I18nUtil.getString("jobinfo_field_jobdesc") + ":{2}" + br +
                I18nUtil.getString("jobconf_monitor_alarm_title") + ":" + I18nUtil.getString("jobconf_monitor_alarm_type") + br +
                I18nUtil.getString("jobconf_monitor_alarm_content") + ":{3}";
        return mailBodyTemplate;
    }

    public boolean sendMsg(String url, String json) {
        //创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, entity, String.class);
        String body = responseEntity.getBody();
        logger.info("sendRobot  url={} body={}", url, body);
        return responseEntity.getStatusCode() == HttpStatus.OK;
    }

    /**
     * @param info
     * @param json
     * @return
     */

    public boolean sendToAll(XxlJobLog jobLog, XxlJobInfo info, String json) {
        Set<String> urlList = new HashSet<>(Arrays.asList(info.getAlarmUrl().split(",")));
        for (String webHookUrl : urlList) {
            // send message
            try {
                sendMsg(webHookUrl, json);
            } catch (Exception e) {
                logger.error(">>>>>>>>>>> xxl-job, job fail alarm webhook send error, JobLogId:{}", jobLog.getId(), e);
                return false;
            }
        }
        return true;
    }

}
