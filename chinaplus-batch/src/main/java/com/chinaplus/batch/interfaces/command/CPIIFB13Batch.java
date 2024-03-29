/**
 * CPIIFB11Batch.java
 * 
 * @screen CPIIFB11
 * @author liu_yinchuan
 */

package com.chinaplus.batch.interfaces.command;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinaplus.batch.common.base.BaseBatch;
import com.chinaplus.batch.common.bean.BaseBatchParam;
import com.chinaplus.batch.common.consts.BatchConst;
import com.chinaplus.batch.common.consts.BatchConst.BatchStatus;
import com.chinaplus.batch.common.consts.BatchConst.InvenByDayParam;
import com.chinaplus.batch.common.consts.BatchConst.SubBatchOfByDay;
import com.chinaplus.batch.interfaces.bean.CPIIFB11Param;
import com.chinaplus.batch.interfaces.bean.TnfBalanceByDayEx;
import com.chinaplus.batch.interfaces.service.CPIIFB11Service;
import com.chinaplus.common.entity.TnmOffice;
import com.chinaplus.common.entity.TntBatchJob;
import com.chinaplus.common.util.ConfigManager;
import com.chinaplus.core.consts.NumberConst.IntDef;
import com.chinaplus.core.exception.BusinessException;
import com.chinaplus.core.util.DateTimeUtil;
import com.chinaplus.core.util.StringUtil;

/**
 * Main process of inventory by day batch.
 * 
 * @author liu_yinchuan
 */
@Component(SubBatchOfByDay.IMP_BALANCE)
public class CPIIFB13Batch extends BaseBatch {

    /** logger */
    private static Logger logger = LoggerFactory.getLogger(CPIIFB13Batch.class);

    /** The Service of inventory by day batch */
    @Autowired
    private CPIIFB11Service byDayService;

    /**
     * create the Batch Parameter.
     * 
     * @param args.
     */
    @Override
    public BaseBatchParam createBatchParam(String[] args) {

        // defined
        CPIIFB11Param param = new CPIIFB11Param();

        // Check batch arguments
        if (args == null || args.length != BatchConst.INT_TWO) {

            // error
            logger.error("The arguments number of batch is incorrect.");
            throw new BusinessException();
        } else {

            // set parameters
            param = new CPIIFB11Param();

            // set parameters
            param.setDate(DateTimeUtil.parseDate(args[InvenByDayParam.DATE], DateTimeUtil.FORMAT_YYYYMMDD));
            // if date is null, error
            if (param.getDate() == null) {
                logger.error("Date is incorrect(Please set Date like:20160223).");
                throw new BusinessException();
            }

            // if office exist, set office code
            if (!StringUtil.isEmpty(args[InvenByDayParam.OFFICE_CODE])) {
                // set office code
                param.setOfficeCode(args[InvenByDayParam.OFFICE_CODE]);
                // check
                TnmOffice office = baseService.getOfficeInfo(param.getOfficeCode());
                if (office == null) {
                    logger.error("Office code does not an effective office.");
                    throw new BusinessException();
                }
                // set office id
                param.setOfficeId(office.getOfficeId());
            } else {
                logger.error("Office code can not be empty.");
                throw new BusinessException();
            }

            // set process date
            param.setProcessDate(baseService.getDBDateTimeByDefaultTimezone());
        }

        // return
        return param;
    }

    /**
     * the batch operate.
     * 
     * @param BaseBatchParam.
     * @return
     */
    @Override
    public boolean doOperate(BaseBatchParam param) throws Exception {
        logger.info("batch CPIIFB13Batch start......");
        // get result
        // cast
        CPIIFB11Param baseParam = (CPIIFB11Param) param;

        // If contains "CPIIFB13", will do Imp Balance By Day information save
        boolean result = this.doPrepareImpBalance(baseParam);

        // check
        if (!result) {
            logger.error("Process of Import Balance has been failed.");
            return result;
        }

        logger.info("batch CPIIFB13Batch end......");

        return result;
    }
    

    /**
     * Do prepare Import balance for table TNF_IMP_STOCK_BY_DAY
     * 
     * @param baseParam base param
     * @throws BusinessException exception
     * 
     * @return successful or not
     */
    private boolean doPrepareImpBalance(CPIIFB11Param baseParam) throws BusinessException {
        
        //  get officeID
        Integer officeId = baseParam.getOfficeId();

        // get Handle Flag is 2, and then set as 98(already process by inventory by day)
        TntBatchJob jobParam = new TntBatchJob();
        jobParam.setBatchId(SubBatchOfByDay.IMP_BALANCE);
        jobParam.setOfficeId(officeId);
        jobParam.setStatus(BatchStatus.SUCCESS);

        // get Latest Batch Time.
        Timestamp lastBatchTime = byDayService.getLatestBatchTime(jobParam);
        Timestamp thisBatchTime = baseParam.getProcessDate();

        // prepare parameter
        TnfBalanceByDayEx balanceParam = new TnfBalanceByDayEx();
        balanceParam.setLastBatchTime(lastBatchTime);
        balanceParam.setCurrBatchTime(thisBatchTime);
        balanceParam.setOfficeId(officeId);

        // get end date
        Date endDate = baseParam.getDate();

        // get last process end date
        Date lastEndDate = byDayService.getLastBalanceDate(balanceParam);
        // set into parameter
        balanceParam.setEndDate(lastEndDate);

        // define
        Map<String, Map<Date, TnfBalanceByDayEx>> balanceByDayMap = new HashMap<String, Map<Date, TnfBalanceByDayEx>>();

        // get the updated end date for imp stock
        Date minEndDate = byDayService.getLastDateOfOrderBalance(balanceParam);
        // get data from database
        if (minEndDate == null) {
            // check date
            if (lastEndDate == null) {
                // check date
                minEndDate = endDate;
            } else {
                // if end date before
                if (endDate.before(lastEndDate)) {
                    // get from last end date
                    minEndDate = endDate;
                } else {
                    // get from last end date
                    minEndDate = lastEndDate;
                }
            }
        }

        // force refresh to last date
        if (lastEndDate != null && endDate.before(lastEndDate)) {
            endDate = lastEndDate;
        }
        
        // if min end date before start date, will do start as start date
        Date bsDate = DateTimeUtil.parseDate(ConfigManager.getBatchStartDate());
        if (bsDate != null && bsDate.after(minEndDate)) {

            // RESET
            minEndDate = DateTimeUtil.parseDate("19000101");

            // do prepare
            // set min end date
            balanceParam.setEndDate(DateTimeUtil.addDay(bsDate, IntDef.INT_ONE));
            balanceParam.setLastEndDate(minEndDate);

            // day order for v_v
            List<TnfBalanceByDayEx> orderInfoList = byDayService.getVVOrderInfoByEachIPOBefore(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, orderInfoList);
            orderInfoList = null;

            // day order for AISIN 
            List<TnfBalanceByDayEx> kanbanInfoList = byDayService.getKanbanInfoByKanbanNoBefore(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, kanbanInfoList);
            kanbanInfoList = null;

            // day order for AISIN
            List<TnfBalanceByDayEx> foreCompleteList = byDayService.getForceCompletedInfoListBefore(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, foreCompleteList);
            foreCompleteList = null;

            // day imp received
            List<TnfBalanceByDayEx> impReceivedInfoList = byDayService.getImpRecevieInfoByOrderBefore(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, impReceivedInfoList);
            impReceivedInfoList = null;

            // save into database
            byDayService.doSaveOrderBalanceListIntoDb(balanceByDayMap, bsDate, null, bsDate, officeId);

            // set end date
            minEndDate = DateTimeUtil.addDay(bsDate, IntDef.INT_ONE);
            // reset last end Date
            if (lastEndDate == null) {
                lastEndDate = bsDate;
            }
        }
        
        // loop
        Date runStartDate = minEndDate;
        Date runEndDate = null;
        Integer tansCount = StringUtil.toInteger(ConfigManager.getBatchTransCount());
        // split when 10 days will as a transaction
        while (!runStartDate.after(endDate)) {

            // clear map
            balanceByDayMap.clear();

            // set run end date
            if (tansCount != null) {
                // add step
                runEndDate = DateTimeUtil.addDay(runStartDate, tansCount);
                // if after end date
                if (runEndDate.after(endDate)) {
                    runEndDate = endDate;
                }
            } else {
                runEndDate = endDate;
            }

            // set min end date
            balanceParam.setEndDate(DateTimeUtil.addDay(runEndDate, IntDef.INT_ONE));
            balanceParam.setLastEndDate(runStartDate);

            // day order for v_v
            logger.info("Start to load VV Order detail information");
            List<TnfBalanceByDayEx> orderInfoList = byDayService.getVVOrderInfoByEachIPO(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, orderInfoList);
            orderInfoList = null;

            // day order for AISIN
            logger.info("Start to load AISIN Kanban detail information");
            List<TnfBalanceByDayEx> kanbanInfoList = byDayService.getKanbanInfoByKanbanNo(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, kanbanInfoList);
            kanbanInfoList = null;

            // day order for AISIN
            logger.info("Start to load Force Completed information");
            List<TnfBalanceByDayEx> foreCompleteList = byDayService.getForceCompletedInfoList(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, foreCompleteList);
            foreCompleteList = null;

            // day imp received
            logger.info("Start to load Imp Inbound information");
            List<TnfBalanceByDayEx> impReceivedInfoList = byDayService.getImpRecevieInfoByOrder(balanceParam);
            byDayService.prepareOrderBalanceList(balanceByDayMap, impReceivedInfoList);
            impReceivedInfoList = null;

            // save into database
            logger.info("Start to prepare order balance information");
            byDayService.doSaveOrderBalanceListIntoDb(balanceByDayMap, runEndDate, lastEndDate, runStartDate, officeId);

            // reset last end Date
            //if (lastEndDate == null || lastEndDate.before(runEndDate)) {
            //    lastEndDate = runEndDate;
            //}

            // set next loop
            runStartDate = DateTimeUtil.addDay(runEndDate, IntDef.INT_ONE);
        }

        // return
        return true;
    }

}
