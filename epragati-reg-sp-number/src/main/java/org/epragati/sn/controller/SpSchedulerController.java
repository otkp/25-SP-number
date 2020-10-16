package org.epragati.sn.controller;

import javax.servlet.http.HttpServletRequest;

import org.epragati.exception.BadRequestException;
import org.epragati.registration.service.ServiceProviderFactory;
import org.epragati.sn.payment.service.SnPaymentGatewayService;
import org.epragati.sn.service.BidClosingProcessService;
import org.epragati.sn.service.NumberSeriesService;
import org.epragati.sn.service.SpecialNumersReleasingService;
import org.epragati.sn.service.SpecialPremiumNumberService;
import org.epragati.util.GateWayResponse;
import org.epragati.util.IPWebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class SpSchedulerController {
	
	private static final Logger logger = LoggerFactory.getLogger(SpSchedulerController.class);

	@Autowired
	private BidClosingProcessService bidClosingProcessService;
	@Autowired
	private SpecialNumersReleasingService bidPaymentProcessService;

	@Autowired
	private IPWebUtils webUtils;
	
	@Value("${isTestSchedulers:false}")
	private boolean isTest;
	
	@Autowired
	private ServiceProviderFactory serviceProviderFactory;
	
	@Autowired
	private SpecialPremiumNumberService specialPremiumNumberService;
	
	
	@PostMapping(path = "/test/generateNumbersInto")
	public String generateNumbersIntoPool(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		
		try {
			if(!isTest) {
				specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			}
			NumberSeriesService numberSeriesService=serviceProviderFactory.getNumberSeriesServiceInstent();
			numberSeriesService.generateNumbersIntoPool();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			logger.error("{}", bre);
			return bre.toString();
		}
	}

	@PostMapping(path = "/test/generateNumbersIntyu")
	public String generateNumbersIntoPoolForOffice(@RequestParam(name = "officeCode") String officeCode,
			@RequestParam(name = "regType") String regType,@RequestHeader("Authorization") String token,HttpServletRequest request) {
		try {
			specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			NumberSeriesService numberSeriesService=serviceProviderFactory.getNumberSeriesServiceInstent();
			numberSeriesService.generateNumbersIntoPoolForOffice(officeCode, regType);
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException e) {
			logger.error("{}", e);
			return e.toString();
		}catch (Exception e) {
			logger.error("{}", e);
			return e.toString();
		}
	}

	@PostMapping(path = "/test/runBidClosingPrasdadasdasd")
	public String bidClosingProcess(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		try {
			if(!isTest) {
				specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			}
			bidClosingProcessService.doBidClosingProcess();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			logger.error("{}", bre);
			return bre.toString();
		} catch (Exception e) {
			logger.error("{}", e);
			return e.toString();
		}
	}


	/**
	 * 
	 * bid.final.payment.verify.services
	 * 
	 */
	@PostMapping(path = "/test/finalPaymentVerifyfasdfasdfas")
	public String finalPaymentVerifyProcess(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		try {
			specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			bidPaymentProcessService.clrearFinalpaymentPendingRecords();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			return bre.toString();
		}
	}

	/**
	 * bid.register.payment.verify.services
	 */
	@PostMapping(path = "/test/regPaymentVerifysdfasdfasdf")
	public String regPaymentVerifyProcess(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		
		try {
			specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			bidPaymentProcessService.clrearSpPaymentPendingRecords();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			return bre.toString();
		}
	}
	/**
	 * bid.register.payment.verify.services
	 */
	@PostMapping(path = "/test/knvldnrefundasdF")
	public String refund(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		
		try {
			specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			bidClosingProcessService.refound();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			return bre.toString();
		}
	}
	
	@GetMapping(value = "/test/handleTRExpiredRecods", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public GateWayResponse<?> handleTRExpiredRecods(@RequestHeader("Authorization") String token,
			HttpServletRequest request) {
		try {
			if(!isTest) {
				specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			}
			specialPremiumNumberService.handleTrExpiredRecords();
			return new GateWayResponse<>(HttpStatus.OK, "Job DONE");
		} catch (Exception e) {
			logger.info("Exception", e);
			return new GateWayResponse<>(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	@Autowired
	private SnPaymentGatewayService paymentGatewayService;
	

	@GetMapping(value = "/test/paymentVeryfy", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public GateWayResponse<?> paymentVeryfy(@RequestHeader("Authorization") String token,HttpServletRequest request) {
		try {
			if(!isTest) {
				specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			}
			paymentGatewayService.verifyAllPaymentFailureRecords();
			return new GateWayResponse<>(HttpStatus.OK, "Job DONE");
		} catch (Exception e) {
			logger.info("Exception", e);
			return new GateWayResponse<>(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	@GetMapping(value = "/test/paymentVeryfySpecific", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public GateWayResponse<?> paymentVeryfySpecific(@RequestHeader("Authorization") String token,
			@RequestParam(name = "VerifySpecific") String VerifySpecific,HttpServletRequest request) {
		try {
			if(!isTest) {
				specialPremiumNumberService.validateRequest(token, webUtils.getClientIp(request));
			}
			return new GateWayResponse<>(paymentGatewayService.VerifySpecific(VerifySpecific));
		} catch (Exception e) {
			logger.info("Exception", e);
			return new GateWayResponse<>(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
