package org.epragati.sn.payment.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.epragati.constants.MessageKeys;
import org.epragati.master.dto.GateWayDTO;
import org.epragati.master.service.InfoService;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.sn.payment.service.SnPaymentGatewayService;
import org.epragati.util.AppMessages;
import org.epragati.util.EncryptDecryptUtil;
import org.epragati.util.GateWayResponse;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.GatewayTypeEnum.PayUParams;
import org.epragati.util.payment.ModuleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class SnPaymentGatewayControler {

	private static final Logger logger = LoggerFactory.getLogger(SnPaymentGatewayControler.class);

	@Value("${sn.ui.payment.success.url}")
	private String paymentSuccessUrl;

	@Value("${sn.ui.payment.failed.url}")
	private String paymentFailedUrl;

	@Value("${sn.ui.payment.pending.url}")
	private String paymentPendingUrl;
	
	@Autowired
	private EncryptDecryptUtil encryptDecryptUtil;
	
	@Autowired
	private SnPaymentGatewayService paymentGatewayService;
	
	@Autowired
	private InfoService infoService;
	
	@Autowired
	private AppMessages appMessages;
	
	@PostMapping(path = "/payUPaymentSuccess", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public void doPaymentSuccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
		GateWayDTO gatewayValue = infoService.findByGateWayType(GatewayTypeEnum.PAYU);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		// logger.info("map data : "+map);
		try {
			Map<String, String[]> data = request.getParameterMap();
			PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
			paymentGateWayResponse.setGatewayResponceMap(data);
			paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.PAYU);
			paymentGateWayResponse = paymentGatewayService.processResponse(paymentGateWayResponse);
			Optional<String> passCodeOptional=paymentGatewayService.updatePaymentStatus(paymentGateWayResponse);
			String an=URLEncoder.encode(encryptDecryptUtil.encrypt(paymentGateWayResponse.getAppTransNo()), "UTF-8");
			if(passCodeOptional.isPresent()) {
				 response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_SUCESS_URL_UI.getParamKey())+"?passCode="+passCodeOptional.get()
				 +"&an="+an+"&module="+ModuleEnum.SPNR);
			}else {
			 response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_SUCESS_URL_UI.getParamKey()) +"?an="+an+"&module="+ModuleEnum.SPNR);
			}
		} catch (Exception e) {
			logger.error("Exception while processing the  payment :{}", e);
			response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_PENDING_URL_UI.getParamKey()));
		}
	}

	@PostMapping(path = "/payUPaymentFailed", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public void doPaymentFailed(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		GateWayDTO gatewayValue = infoService.findByGateWayType(GatewayTypeEnum.PAYU);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		try {
			
			Map<String, String[]> data = request.getParameterMap();
			PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
			paymentGateWayResponse.setGatewayResponceMap(data);
			paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.PAYU);
			paymentGatewayService.processResponse(paymentGateWayResponse);
			paymentGatewayService.updatePaymentStatus(paymentGateWayResponse);
			String errorMsg=StringUtils.EMPTY;
			if(null!=paymentGateWayResponse.getPayUResponse() && null!=paymentGateWayResponse.getPayUResponse().getError_Message()){
				errorMsg= paymentGateWayResponse.getPayUResponse().getError_Message();
			}
			String an=URLEncoder.encode(encryptDecryptUtil.encrypt(paymentGateWayResponse.getAppTransNo()), "UTF-8");
			response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_FAILURE_URL_UI.getParamKey()) +"?an="+an+"&module="+ModuleEnum.SPNR+" &error="+errorMsg);
		} catch (Exception e) {
			logger.error("Exception while processing the  payment :{e}", e);
			response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_PENDING_URL_UI.getParamKey()));
		}
	}
	
//	@PostMapping(path = "test/payUpdate", produces = { MediaType.APPLICATION_JSON_VALUE,
//			MediaType.APPLICATION_XML_VALUE })
//	public void testDoPayments(HttpServletRequest request, HttpServletResponse response) throws IOException{
//		
//		//GateWayDTO gatewayValue = infoService.findByGateWayType(GatewayTypeEnum.PAYU);
//	//	Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
//		try {
//			
//			PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
//			PayUResponse payUResponse =new PayUResponse();
//			payUResponse.setPayuMoneyId("200842138");
//			paymentGateWayResponse.setPayUResponse(payUResponse);
//			paymentGateWayResponse.setPaymentStatus(PayStatusEnum.SUCCESS);
//			paymentGateWayResponse.setAppTransNo("5b501a037c854f50b798464e");
//			paymentGateWayResponse.setModuleCode(ModuleEnum.SPNR.getCode());
//			paymentGateWayResponse.setTransactionNo("a23c3d7b-9a24-4921-99d7-4467b19459ca");
//			paymentGatewayService.updatePaymentStatus(paymentGateWayResponse);
//		} catch (Exception e) {
//			logger.error("Exception while processing the  payment :{e}", e);
//		//	response.sendRedirect(gatewayDetails.get(PayUParams.CITIZEN_PENDING_URL_UI.getParamKey()));
//		}
//	}
	
	
	@GetMapping(value = "decriptData", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public GateWayResponse<?> decryptedData(
			@RequestParam(value = "encryptedData") String encryptedData) {
		try {
			return new GateWayResponse<>(HttpStatus.OK,encryptDecryptUtil.decrypt(encryptedData),
					appMessages.getResponseMessage(MessageKeys.MESSAGE_SUCCESS));
			
		} catch (Exception e) {
			return new GateWayResponse<>(HttpStatus.OK, e.getMessage());
		}

	}

}
