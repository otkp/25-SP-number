package org.epragati.sn.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.epragati.auditService.AuditLogsService;
import org.epragati.constants.Schedulers;
import org.epragati.registration.service.ServiceProviderFactory;
import org.epragati.sn.service.BidClosingProcessService;
import org.epragati.sn.service.NumberSeriesService;
import org.epragati.sn.service.SpecialNumersReleasingService;
import org.epragati.sn.service.SpecialPremiumNumberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class SpecialNumberScheduler {

	private static final Logger logger = LoggerFactory.getLogger(SpecialNumberScheduler.class);

	@Value("${scheduler.bid.closing.process.isEnable:false}")
	private Boolean isBidClosingProcessSchedulerEnable;

	@Value("${scheduler.bid.number.pool.update.isEnable:false}")
	private Boolean isBidNumberPoolUpdateSchedulerEnable;
	
	@Value("${scheduler.bid.release.sn.process.isEnable}")
	private Boolean isPaymentPendingSnAvailable;
	@Value("${bid.final.payment.verify.services.isEnable}")
	private Boolean isFinalPaymentPendingAvailable;
	
	@Value("${bid.register.payment.verify.services.isEnable}")
	private Boolean isSPPaymentPendingAvailable;
	
	@Value("${scheduler.reg.tr.expired.isEnable}")
	private Boolean isTrExpiredSchedulerEnable;
	
	@Autowired
	private BidClosingProcessService bidClosingProcessService;
	
	@Autowired
	private SpecialNumersReleasingService specialNumersReleasingService;
	
	@Autowired
	private AuditLogsService auditLogsService;
	
	@Autowired
	private ServiceProviderFactory serviceProviderFactory;
	
	@Autowired
	private SpecialPremiumNumberService specialPremiumNumberService;
	
	

	/**
	 * Do bid closing process for all series at all offices
	 */
	@Scheduled(cron = "${scheduler.bid.closing.process.interval}")
	public void doBidClosingProcessScheduler() {
		LocalDateTime startTime=LocalDateTime.now();
		LocalDateTime endTime=null;
		Boolean isExecuteSucess=true;
		String error=null;
		List<String> internalErrors=null;
		if (isBidClosingProcessSchedulerEnable) {
			logger.info("Bid closing process scheduler starting at time is now {}", LocalDateTime.now());

			try {
				internalErrors=bidClosingProcessService.doBidClosingProcess();
			} catch (Exception ex) {
				error= ex.getMessage();
				isExecuteSucess=false;
				logger.error("Exception occured while Bid closing process scheduler running", ex);
			}
			endTime=LocalDateTime.now();
			logger.info("Bid closing process scheduler end at time is now {}", endTime);
		} else {
			isExecuteSucess=false;
			endTime=LocalDateTime.now();
			error= "Bid closing process scheduler is skiped by flag  at "+endTime;
			logger.info("Bid closing process scheduler is skiped by flag  at {}", endTime);
		}
		auditLogsService.saveScedhuleLogs(Schedulers.BIDCLOSING,startTime,endTime,isExecuteSucess,error,internalErrors);
		
	}

	/**
	 * Do create/update number pool series of all offices
	 */
	@Scheduled(cron = "${scheduler.bid.number.pool.update.interval}")
	public void createOrUpdateNumberPoolSeriesScheduler() {
		LocalDateTime startTime=LocalDateTime.now();
		LocalDateTime endTime=null;
		Boolean isExecuteSucess=true;
		String error=null;
		List<String> internalErrors=null;
		if (isBidNumberPoolUpdateSchedulerEnable) {
			logger.info("Bid number pool update scheduler starting at time is now {}", LocalDateTime.now());

			try {
				NumberSeriesService numberSeriesService=serviceProviderFactory.getNumberSeriesServiceInstent();
				internalErrors = numberSeriesService.generateNumbersIntoPool();

			} catch (Exception ex) {
				error= ex.getMessage();
				isExecuteSucess=false;
				logger.error("Exception occured while Bid number pool update scheduler running", ex);
			}
			endTime=LocalDateTime.now();
			logger.info("Bid closing process scheduler end at time is now {}", endTime);
			logger.info("Bid number pool update scheduler end at time is now {}", LocalDateTime.now());

		} else {
			isExecuteSucess=false;
			endTime=LocalDateTime.now();
			error= "Create number pool scheduler skiped by flag  at "+endTime;
			logger.info("Bid number pool update scheduler is skiped at time is now {}", LocalDateTime.now());
		}
		auditLogsService.saveScedhuleLogs(Schedulers.NUMBERPOOL,startTime,endTime,isExecuteSucess,error,internalErrors);

	}
	/**
	 *  FINAL PAYMENT STATUS CHANGE  SCHEDULER 
	 */
	
	@Scheduled(cron = "${bid.final.payment.verify.services}")
	public void finalpaymentPendingScheduler() {
		LocalDateTime startTime=LocalDateTime.now();
		LocalDateTime endTime=null;
		Boolean isExecuteSucess=true;
		String error=null;
		List<String> internalErrors=null;
		if (isFinalPaymentPendingAvailable) {
			logger.info("final payment verify  scheduler starting at time is now {}", LocalDateTime.now());

			try {
				internalErrors=  specialNumersReleasingService.clrearFinalpaymentPendingRecords();
				
			} catch (Exception ex) {
				error= ex.getMessage();
				isExecuteSucess=false;
				logger.error("Exception occured while executing  final payment pending scheduler running", ex);
			}
			endTime=LocalDateTime.now();
			logger.info(" final payment verify scheduler end at time is now {}", LocalDateTime.now());

		} else {
			isExecuteSucess=false;
			endTime=LocalDateTime.now();
			error= "Final payment verify scheduler is skiped by flag  at "+endTime;
			logger.info(" final payment verify scheduler is skiped at time is now {}", LocalDateTime.now());
		}

		auditLogsService.saveScedhuleLogs(Schedulers.FINALPAYMENTVERIFY,startTime,endTime,isExecuteSucess,error,internalErrors);
	}
	
	@Scheduled(cron = "${bid.register.payment.verify.services}")
	public void spPaymentPendingScheduler() {
		LocalDateTime startTime=LocalDateTime.now();
		LocalDateTime endTime=null;
		Boolean isExecuteSucess=true;
		String error=null;
		List<String> internalErrors=null;
		if (isSPPaymentPendingAvailable) {
			logger.info("SP number Registration payment verify scheduler starting at time is now {}", LocalDateTime.now());

			try {
				internalErrors=  specialNumersReleasingService.clrearSpPaymentPendingRecords();
				
			} catch (Exception ex) {
				error= ex.getMessage();
				isExecuteSucess=false;
				logger.error("Exception occured while executing  SP number Registration payment verify scheduler running", ex);
			}
			endTime=LocalDateTime.now();
			logger.info("SP number Registration payment verify scheduler end at time is now {}", LocalDateTime.now());

		} else {
			isExecuteSucess=false;
			endTime=LocalDateTime.now();
			error= "SP payment verify scheduler is skiped by flag  at "+endTime;
			logger.info("SP number Registration payment verify scheduler is skiped at time is now {}", LocalDateTime.now());
		}
		auditLogsService.saveScedhuleLogs(Schedulers.SPPAYMENTVERIFY,startTime,endTime,isExecuteSucess,error,internalErrors);
	}
	
	
	
	
	/**
	 * Releasing or Reserving Special Number Based on the Payment Status
	 */
	/*@Scheduled(cron = "${scheduler.bid.release.sn.process.interval}")
	public void reserveOrOpenSpecialNumber() {

		if(isPaymentPendingSnAvailable){
			logger.info("special numbers reserving or releasing based on the payment status scheduler starting at time is now {}", LocalDateTime.now());
			try{
				specialNumersReleasingService.SpecialNumberReleaseService();
				
			}catch(Exception ex){
				logger.error("Exception occured while special numbers reserving or releasing service running", ex);
			}
			logger.info("special numbers reserving or releasing service scheduler end at time is now {}", LocalDateTime.now());
		}
		else {
			logger.info("special numbers reserving or releasing service scheduler is skiped at time is now {}", LocalDateTime.now());
		}
	}*/
	
	//Dummy Refound Scheduler
	
	
	
	@Scheduled(cron = "${scheduler.reg.tr.expired.cron}")
	public void handleTrExpiredRecordsScheduler() {

		LocalDateTime startTime = LocalDateTime.now();
		LocalDateTime endTime = null;
		Boolean isExecuteSucess = true;
		String error = null;
		if (isTrExpiredSchedulerEnable) {
			logger.info("HandleTrExpiredRecords	 scheduler starting at time is now {}", LocalDateTime.now());

			try {
				specialPremiumNumberService.handleTrExpiredRecords();

			} catch (Exception ex) {
				error = ex.getMessage();
				isExecuteSucess = false;
				logger.error("Exception occured while Tr ExpiredScheduler (TREXPIRED) enable scheduler running", ex);
			}
			endTime = LocalDateTime.now();
			logger.info("TREXPIRED scheduler end at time is now {}", endTime);

		} else {
			isExecuteSucess = false;
			endTime = LocalDateTime.now();
			logger.info("TREXPIRED scheduler is skiped at time is now {}", endTime);
		}
		auditLogsService.saveScedhuleLogs(Schedulers.TREXPIRED, startTime, endTime, isExecuteSucess, error, null);
	}
	
}
