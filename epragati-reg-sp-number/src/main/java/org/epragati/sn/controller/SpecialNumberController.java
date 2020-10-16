package org.epragati.sn.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.epragati.aadhar.APIResponse;
import org.epragati.constants.CovCategory;
import org.epragati.exception.BadRequestException;
import org.epragati.master.service.OfficeService;
import org.epragati.master.service.PrSeriesService;
import org.epragati.payments.vo.PaymentReqParams;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.registration.service.ServiceProviderFactory;
import org.epragati.sn.dto.SpecialNumberDetailsDTO;
import org.epragati.sn.payment.service.SnPaymentGatewayService;
import org.epragati.sn.service.BidDetailsService;
import org.epragati.sn.service.NumberSeriesService;
import org.epragati.sn.service.SpecialPremiumNumberService;
import org.epragati.sn.vo.BidConfigMasterVO;
import org.epragati.sn.vo.BidIncrementalAmountInput;
import org.epragati.sn.vo.BindFinalAmountInput;
import org.epragati.sn.vo.LeftOverVO;
import org.epragati.sn.vo.NumberSeriesSelectionInput;
import org.epragati.sn.vo.SearchPaymentStatusVO;
import org.epragati.sn.vo.SpecialFeeAndNumberDetailsVO;
import org.epragati.sn.vo.SpecialNumberDetailsVo;
import org.epragati.util.BidStatus;
import org.epragati.util.DateUtils;
import org.epragati.util.IPWebUtils;
import org.epragati.util.SourceUtil;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.ModuleEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.ServiceEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@Validated
public class SpecialNumberController {

	private static final Logger logger = LoggerFactory.getLogger(SpecialNumberController.class);

	@Autowired
	private SpecialPremiumNumberService specialPremiumNumberService;

	@Autowired
	private BidDetailsService bidDetailsService;

	@Autowired
	private SnPaymentGatewayService paymentGatewayService;

	@Autowired
	private DateUtils dateUtils;

	@Autowired
	private IPWebUtils webUtils;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Autowired
	private PrSeriesService prSeriesService;

	@Value("${isTestSchedulers:false}")
	private boolean isTest;

	@GetMapping(value = "/getBidConfigDetails", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getBidConfigDetails() {

		try {
			Optional<BidConfigMasterVO> resultOptional = prSeriesService.getBidConfigMasterData(Boolean.TRUE);
			if (resultOptional.isPresent()) {
				return new APIResponse<>(true, HttpStatus.OK, resultOptional.get());
			}
			return new APIResponse<String>(HttpStatus.NOT_FOUND, "No active bid config details found.");
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/getNumberSeries", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getNumberSeries(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo,
			@RequestParam(name = "isPrNo", required = false, defaultValue = "false") boolean isPrNo,
			@RequestParam(name = "rang", required = false) String rang,
			@RequestParam(name = "prSeriesId", required = false) String prSeriesId) {

		logger.info("trNumber [{}], mobileNo [{}]", trNumber, mobileNo);

		try {
			synchronized (trNumber.intern()) {
				BidConfigMasterVO bidConfigMaster = bidDetailsService.isSpecialNumberRegistrationDurationInBetween();

				SpecialFeeAndNumberDetailsVO result = specialPremiumNumberService.getNumberSerialsByTrNumber(trNumber,
						mobileNo, bidConfigMaster, isPrNo, rang, prSeriesId);

				Long duration = dateUtils.diff(bidConfigMaster.getSpecialNumberRegStartTime(),
						bidConfigMaster.getSpecialNumberRegEndTime()).getSeconds();
				result.setRegistrationDuration(duration);

				return new APIResponse<>(true, HttpStatus.OK, result);
			}
		} catch (BadRequestException bre) {
			logger.error("{}", bre.getMessage());
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@GetMapping(value = "/getAvailableNumberSeries", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getAvailableNumberSeries(
			@RequestParam(name = "officeCode", required = false) String officeCode,
			@RequestParam(name = "covType") CovCategory covType,
			@RequestParam(name = "rang", required = false) String rang,
			@RequestParam(name = "prSeriesId", required = false) String prSeriesId) {

		logger.info("/getAvailableNumberSeries Start inputs [officeCode: {},covType:{} ]", officeCode, covType);

		try {

			NumberSeriesService numberSeriesService = serviceProviderFactory.getNumberSeriesServiceInstent();
			SpecialFeeAndNumberDetailsVO result = numberSeriesService.getNumberSeriesByOfficeCode(officeCode, covType,
					rang, prSeriesId);

			return new APIResponse<>(true, HttpStatus.OK, result);
		} catch (BadRequestException bre) {
			logger.error("{}", bre.getMessage());
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@Autowired
	private OfficeService officeService;

	@GetMapping(value = "/getAllOffices", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getAllOffices() {

		try {
			return new APIResponse<>(true, HttpStatus.OK, officeService.findRTAOffices());
		} catch (BadRequestException bre) {
			logger.error("{}", bre.getMessage());
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@PostMapping(path = "/doSpecialPremiumPay", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doSpecialPremiumPay(@RequestBody @Validated NumberSeriesSelectionInput input) {

		logger.debug("NumberSeriesSelectionInput [{}] ", input);

		try {

			synchronized (input.getTrNumber().intern()) {
				BidConfigMasterVO bidConfigMaster = bidDetailsService.isSpecialNumberRegistrationDurationInBetween();
				SpecialNumberDetailsDTO result = specialPremiumNumberService.doSpecialPremiumPay(input,
						bidConfigMaster);
				return doSpecialNumberPaymentProcess(result, false);
			}

		} catch (BadRequestException bre) {
			logger.error("{}", bre.getMessage());
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@GetMapping(path = "/doSpecialPremiumRepay", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doSpecialPremiumRepay(@RequestParam("specialNumberAppId") String specialNumberAppId) {

		logger.debug("specialNumberAppId : [{}] ", specialNumberAppId);
		try {
			synchronized (specialNumberAppId.intern()) {
				bidDetailsService.isSpecialNumberRegistrationDurationInBetween();
				SpecialNumberDetailsDTO specialNumberDetails = specialPremiumNumberService
						.doSpecialPremiumRepay(specialNumberAppId);

				return doSpecialNumberPaymentProcess(specialNumberDetails, false);
			}

		} catch (BadRequestException bre) {
			logger.error("{}", bre.getMessage());
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@GetMapping(path = "/doSpecialPremiumVerify", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doSpecialPremiumVerify(@RequestParam("specialNumberAppId") String specialNumberAppId) {

		logger.debug("specialNumberAppId : [{}] ", specialNumberAppId);

		try {
			return new APIResponse<PayStatusEnum>(true, HttpStatus.OK, specialPremiumNumberService
					.processToverifyPayments(specialNumberAppId, ModuleEnum.SPNR, SourceUtil.CITIZEN.getName()));
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	private APIResponse<PaymentReqParams> doSpecialNumberPaymentProcess(SpecialNumberDetailsDTO entity,
			boolean isBidAmount) {

		TransactionDetailVO transactionDetails = new TransactionDetailVO();

		transactionDetails.setEmail(entity.getCustomerDetails().getEmailId());
		transactionDetails.setPhone(entity.getCustomerDetails().getMobileNo());
		transactionDetails.setFirstName(
				entity.getCustomerDetails().getFirstName() + " " + entity.getCustomerDetails().getLastName());
		transactionDetails.setRemiterName(transactionDetails.getFirstName());

		transactionDetails.setFormNumber(entity.getSpecialNumberAppId());
		if (isBidAmount) {
			transactionDetails.setAmount(entity.getBidFinalDetails().getBidAmount());
			transactionDetails.setServicesFeeAmt(Double.valueOf(0));
			transactionDetails.setModule(ModuleEnum.SPNB.getCode());
			transactionDetails.setServiceEnumList(Arrays.asList(ServiceEnum.SPNB));
			transactionDetails.setOfficeCode(entity.getVehicleDetails().getRtaOffice().getOfficeCode());
			transactionDetails.setTxnid(entity.getBidFinalDetails().getTransactionNo());
		} else {
			transactionDetails.setAmount(entity.getSpecialNumberFeeDetails().getTotalAmount());
			transactionDetails.setServicesFeeAmt(entity.getSpecialNumberFeeDetails().getServicesAmount());
			transactionDetails.setModule(ModuleEnum.SPNR.getCode());
			transactionDetails.setServiceEnumList(Arrays.asList(ServiceEnum.SPNR));
			transactionDetails.setOfficeCode(entity.getVehicleDetails().getRtaOffice().getOfficeCode());
			transactionDetails.setTxnid(entity.getSpecialNumberFeeDetails().getTransactionNo());
		}

		transactionDetails.setGatewayTypeEnum(GatewayTypeEnum.PAYU);

		paymentGatewayService.prepareRequestObject(transactionDetails);

		// transactionDetails.setSucessUrl("https://localhost:8182/sn/payUPaymentSuccess");
		// transactionDetails.setFailureUrl("https://localhost:8182/sn/payUPaymentFailed");

		return new APIResponse<>(true, HttpStatus.OK,
				paymentGatewayService.convertPayments(transactionDetails, entity.getSpecialNumberAppId()));
	}

	@PostMapping(value = "/doBidLogin", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doBidLogin(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo, @RequestParam(name = "passcode") String passcode,
			@RequestParam(name = "isBidLogin", required = false, defaultValue = "false") Boolean isBidLogin) {

		try {
			Optional<SpecialNumberDetailsVo> voOptional = bidDetailsService.getByTrNumberAndPasscode(trNumber, mobileNo,
					passcode);

			if (!voOptional.isPresent()) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "No record found.");
			}
			if (isBidLogin) {

				if (isBidLogin
						&& !voOptional.get().getBidStatus().getCode().equals(BidStatus.SPPAYMENTDONE.getCode())) {
					return new APIResponse<String>(HttpStatus.NOT_FOUND,
							"Invalid status to allow the e bidding. Your current status is: "
									+ voOptional.get().getBidStatus());
				}

				Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

				if (!durationOptional.isPresent()) {
					return new APIResponse<String>(HttpStatus.NOT_FOUND, "Currently not available for eBidding.");
				}

				voOptional.get().setBidDuration(durationOptional.get());
			}
			voOptional.get().setParticipantsCount(specialPremiumNumberService
					.getParticipantsCount(voOptional.get().getBidVehicleDetails().getBidNumberDtlsId()));
			String prNo = voOptional.get().getSelectedPrSeries();
			voOptional.get()
					.setBidDescription((voOptional.get().getBidStatus().getContent().replace("@@PRNO@@", prNo)));

			return new APIResponse<>(true, HttpStatus.OK, voOptional.get());

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/bidSearchwithTrAndMobile", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> bidSearchwithTrAndMobile(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo) {

		try {
			logger.info("In bidSearchwithTrAndMobile(), Inputs trNumber: {}, mobileNo: {}", trNumber, mobileNo);
			Optional<SpecialNumberDetailsVo> voOptional = bidDetailsService.getByTrNumberAndMobileNo(trNumber,
					mobileNo);

			if (!voOptional.isPresent()) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "No record found.");
			}
			String prNo = voOptional.get().getSelectedPrSeries();
			voOptional.get()
					.setBidDescription((voOptional.get().getBidStatus().getContent().replace("@@PRNO@@", prNo)));

			return new APIResponse<>(true, HttpStatus.OK, voOptional.get());

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/getBidderDetails", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getBidderDetails(@RequestParam(name = "specialNumberAppId") String specialNumberAppId) {

		try {
			Optional<SpecialNumberDetailsVo> voOptional = bidDetailsService.getBidderDetails(specialNumberAppId);

			if (!voOptional.isPresent()) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "No record found.");
			}

			Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

			if (durationOptional.isPresent()) {
				voOptional.get().setBidDuration(durationOptional.get());
				voOptional.get().setParticipantsCount(specialPremiumNumberService
						.getParticipantsCount(voOptional.get().getBidVehicleDetails().getBidNumberDtlsId()));
			}

			return new APIResponse<>(true, HttpStatus.OK, voOptional.get());

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@PostMapping(path = "/doBidLeft", produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doBidLeft(@RequestBody @Validated BindFinalAmountInput input) {

		logger.debug("BindFinalAmountInput [{}] ", input);
		try {

			bidDetailsService.doBidLeft(input);

			return new APIResponse<>(true, HttpStatus.OK, "Success");
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@PostMapping(path = "/addBidIncrementalAmount", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> addBidIncrementalAmount(@RequestBody @Validated BidIncrementalAmountInput input) {

		logger.debug("BidIncrementalAmountInput [{}] ", input);
		try {
			synchronized (input.getSpecialNumberAppId().intern()) {
				Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

				if (!durationOptional.isPresent()) {
					return new APIResponse<String>(HttpStatus.NOT_FOUND, "Bid is not with in duration.");
				}

				return new APIResponse<>(true, HttpStatus.OK, bidDetailsService.addBidIncrementalAmount(input));
			}
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(path = "/clearBidIncrementalAmount", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> clearBidIncrementalAmount(
			@RequestParam(name = "specialNumberAppId") String specialNumberAppId) {

		logger.debug("BidIncrementalAmountInput [{}] ", specialNumberAppId);
		try {
			synchronized (specialNumberAppId.intern()) {
				Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

				if (!durationOptional.isPresent()) {
					return new APIResponse<String>(HttpStatus.NOT_FOUND, "Bid is not with in duration.");
				}
				bidDetailsService.clearBidIncrementalAmount(specialNumberAppId);
				return new APIResponse<>(true, HttpStatus.OK, Boolean.TRUE);
			}
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@PostMapping(path = "/doBidFinalPay", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doBidFinalPay(@RequestBody @Validated BindFinalAmountInput input) {

		logger.debug("BindFinalAmountInput [{}] ", input);
		try {
			Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

			if (!durationOptional.isPresent()) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "e-Bidding closed today.");
			}
			synchronized (input.getSpecialNumberAppId().intern()) {
				SpecialNumberDetailsDTO result = bidDetailsService.doBidFinalPay(input);

				return doSpecialNumberPaymentProcess(result, true);
			}

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(path = "/doBidFinalRepay", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doBidFinalRepay(@RequestParam("specialNumberAppId") String specialNumberAppId) {

		logger.debug("specialNumberAppId : [{}] ", specialNumberAppId);
		try {
			Optional<Long> durationOptional = bidDetailsService.isBidDurationInBetween();

			if (!durationOptional.isPresent()) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "e-Bidding closed today.");
			}
			synchronized (specialNumberAppId.intern()) {
				SpecialNumberDetailsDTO specialNumberDetails = bidDetailsService.doBidFinalRepay(specialNumberAppId);

				return doSpecialNumberPaymentProcess(specialNumberDetails, true);
			}

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(path = "/doBidFinalVerify", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> doBidFinalVerify(@RequestParam("specialNumberAppId") String specialNumberAppId) {

		logger.debug("specialNumberAppId : [{}] ", specialNumberAppId);

		try {
			return new APIResponse<PayStatusEnum>(true, HttpStatus.OK, specialPremiumNumberService
					.processToverifyPayments(specialNumberAppId, ModuleEnum.SPNB, SourceUtil.CITIZEN.getName()));
		} catch (BadRequestException e) {
			logger.error("doBidFinalVerify : {}", e.getMessage());
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("doBidFinalVerify : {}", e);
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		}

	}

	@PostMapping(value = "/searchPaymentStatus", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> searchPaymentStatus(@RequestParam(name = "trNumber") String trNumber) {

		logger.debug("searchPaymentStatus for specialNumberAppId : [{}] ", trNumber);
		try {
			synchronized (trNumber.intern()) {
				SearchPaymentStatusVO vo = bidDetailsService.searchPaymentStatus(trNumber);
				return new APIResponse<SearchPaymentStatusVO>(true, HttpStatus.OK, vo);
			}
		} catch (BadRequestException e) {
			logger.error("searchPaymentStatus : {}", e.getMessage());
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("searchPaymentStatus : {}", e);
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		}

	}

	@GetMapping(value = "/resendPassCode", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> sendPassCode(@RequestParam(name = "trNo") String trNo,
			@RequestParam(name = "mobileNo") String mobileNo) {

		logger.debug("resendPassCode for specialNumberAppId : [{}] ", trNo);
		try {
			synchronized (trNo.intern()) {
				specialPremiumNumberService.resendPassCodeAlert(trNo, mobileNo);
			}
			return new APIResponse<Boolean>(true, HttpStatus.OK, Boolean.TRUE);
		} catch (BadRequestException e) {
			logger.error("sendPassCode : {}", e.getMessage());
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("sendPassCode : {}", e);
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		}

	}

	@GetMapping(value = "/viewPassCode", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> viewPassCode(@RequestParam(name = "trNo") String trNo,
			@RequestParam(name = "mobileNo") String mobileNo) {

		logger.debug("resendPassCode for specialNumberAppId : [{}] ", trNo);
		try {
			Optional<String> passCode = specialPremiumNumberService.viewPassCode(trNo, mobileNo);
			if (!passCode.isPresent()) {
				logger.error("Passcode not generated");
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "Passcode not generated");
			}

			return new APIResponse<String>(true, HttpStatus.OK, passCode.get());
		} catch (BadRequestException e) {
			logger.error("viewPassCode : {}", e.getMessage());
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("viewPassCode : {}", e);
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	@GetMapping(value = "/bidLeftFlagEnableing", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> bidSearchwithTrAndChassisAndMobileNo(@RequestParam(name = "trNo") String trNo,
			@RequestParam(name = "chassisNumber") String chassisNumber, @RequestParam(name = "mobile") String mobile,
			@RequestParam(name = "isBidLeftRequest") boolean isBidLeftRequest,
			@RequestParam(name = "isPrNo", required = false) boolean isPrNo) {

		try {
			logger.info("In bidSearchwithTrAndChassisAndMobileNo(), Inputs trNumber: {}, chassisNo: {}, mobileNo: {}",
					trNo, chassisNumber, mobile);
			String bidStatusFlag = bidDetailsService.getByTrNoAndChassisNumberAndMobile(trNo, chassisNumber, mobile,
					isPrNo);

			if (StringUtils.isEmpty(bidStatusFlag)) {
				logger.info("bidStatusFlag coming as [{}]", bidStatusFlag);
				return new APIResponse<String>(HttpStatus.NOT_FOUND, "No record found.");
			}
			if (isBidLeftRequest) {
				if (!Arrays.asList(BidStatus.BIDLEFT.getDescription(), BidStatus.SPNOTREQUIRED.getDescription())
						.contains(bidStatusFlag)) {
					return new APIResponse<String>(HttpStatus.NOT_FOUND,
							"You are already perform action on ‘Convert  Ordinary Number’ (OR) Current you are not eligible for special number selection.");
				}
			} else if (!BidStatus.SPREQUIRED.getDescription().equals(bidStatusFlag)) {
				return new APIResponse<String>(HttpStatus.NOT_FOUND,
						"You are already perform action on ‘Convert  Ordinary Number’ (OR) Current you are not eligible for special number selection.");
			}

			return new APIResponse<>(true, HttpStatus.OK, bidStatusFlag);

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/specialNumberAlterationProcess", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> specialNumberAlterationProcess(@RequestParam(name = "trNo") String trNo,
			@RequestParam(name = "chassisNumber") String chassisNumber, @RequestParam(name = "mobile") String mobile,
			@RequestParam(name = "isPrNo", required = false) boolean isPrNo, HttpServletRequest request) {

		try {
			logger.info("In specialNumberAlterationProcess(), Inputs trNumber: {}, chassisNo: {}, mobileNo: {}", trNo,
					chassisNumber, mobile);
			synchronized ((trNo + mobile + chassisNumber).intern()) {
				bidDetailsService.specialNumberAlterationProcess(trNo, chassisNumber, mobile,
						webUtils.getClientIp(request), isPrNo);
			}

			return new APIResponse<>(true, HttpStatus.OK, "Success");

		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/getLeftOverNumberSeries", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getLeftOverNumberSeries(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo,
			@RequestParam(name = "isPrNo", required = false, defaultValue = "false") boolean isPrNo) {
		Set<String> leftOverList = null;
		try {
			logger.info("getLeftOverNumberSeries(), Inputs  trNumber: {}, mobileNo: {}", trNumber, mobileNo);
			synchronized (trNumber.intern()) {
				// NumberSeriesService
				// numberSeriesService=serviceProviderFactory.getNumberSeriesServiceInstent();
				leftOverList = specialPremiumNumberService.getListOfLeftOverNumberSeries(trNumber, mobileNo, isPrNo,
						bidDetailsService.isSpecialNumberRegistrationDurationInBetween());

			}

			return new APIResponse<>(true, HttpStatus.OK, leftOverList);
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	// Left Over drop down
	@GetMapping(value = "/getLeftOverNumbersDetails", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getLeftOverNumbersDetails(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo, @RequestParam(name = "prSeries") String prSeries,
			@RequestParam(name = "isPrNo", required = false, defaultValue = "false") boolean isPrNo) {
		List<LeftOverVO> leftOverList = null;
		try {
			logger.info(" getLeftOverNumbers() Start");

			leftOverList = specialPremiumNumberService.getListOfLeftOverNumbers(trNumber, mobileNo, prSeries, isPrNo,
					bidDetailsService.isSpecialNumberRegistrationDurationInBetween());
			return new APIResponse<>(true, HttpStatus.OK, leftOverList);
		} catch (BadRequestException e) {
			logger.error(
					"In getLeftOverNumbers BadRequestException:- Inputs  trNumber: {}, mobileNo: {}, prSeries{} and BadRequestException: {}",
					trNumber, mobileNo, prSeries, e);
			return new APIResponse<>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error(
					"In getLeftOverNumbers Exception:- Inputs  trNumber: {}, mobileNo: {}, prSeries{} and Exception: {}",
					trNumber, mobileNo, prSeries, e);
			return new APIResponse<>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@PostMapping(path = "/test/generateMissingszdfsdfvdfgsdfgsdfg")
	public String generateMissingPrNONumbers(@RequestHeader("Authorization") String token, HttpServletRequest request) {
		try {
			// NumberSeriesService
			// numberSeriesService=serviceProviderFactory.getNumberSeriesServiceInstent();
			// specialPremiumNumberService.validateRequest(token,
			// webUtils.getClientIp(request));
			// numberSeriesService.prSeriesMissing();
			return "<h1>Job DONE<h1>";
		} catch (BadRequestException bre) {
			return bre.toString();
		}
	}

	// leftOver Avalible List
	@GetMapping(value = "/getLeftOverAvalibleSeries", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getLeftOverAvalibleSeries(
			@RequestParam(name = "officeCode", required = false) String officeCode,
			@RequestParam(name = "regType") CovCategory regType) {
		Set<String> leftOverList = null;
		try {
			logger.info("getLeftOverAvalibleSeries(), Inputs  trNumber: {}, mobileNo: {}", officeCode, regType);
			NumberSeriesService numberSeriesService = serviceProviderFactory.getNumberSeriesServiceInstent();
			leftOverList = numberSeriesService.getListOfLeftOverAvalibleSeries(officeCode, regType);
			if (leftOverList.size() == 0) {
				return new APIResponse<String>(HttpStatus.BAD_REQUEST, "Series Type not found");
			}
			return new APIResponse<>(true, HttpStatus.OK, leftOverList);
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/getAvalibleLeftOverNumbers", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getAvalibleLeftOverNumbers(
			@RequestParam(name = "officeCode", required = false) String officeCode,
			@RequestParam(name = "regType") CovCategory regType,
			@RequestParam(name = "prSeries") @Size(max = 4, message = "please pass valid Series") String prSeries) {
		List<LeftOverVO> leftOverList = null;
		try {
			logger.info(" getAvalibleLeftOverNumbers(), Inputs  officeCode: {}, regType: {}, prSeries{}", officeCode,
					regType, prSeries);

			NumberSeriesService numberSeriesService = serviceProviderFactory.getNumberSeriesServiceInstent();
			leftOverList = numberSeriesService.getrAvalibleLeftOverNumbers(officeCode, regType, prSeries);
			return new APIResponse<>(true, HttpStatus.OK, leftOverList);
		} catch (BadRequestException e) {
			logger.error("{}", e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("{}", e);
			return new APIResponse<String>(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
	}

	@GetMapping(value = "/getNumberRang", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getNumberRang(@RequestParam(name = "regType") CovCategory regType) {
		try {
			NumberSeriesService numberSeriesService = serviceProviderFactory.getNumberSeriesServiceInstent();
			return new APIResponse<>(true, HttpStatus.OK, numberSeriesService.getNumberRange(regType, Boolean.TRUE));
		} catch (BadRequestException e) {
			logger.error("Inputs param regType: {}. Exception:   {}", regType, e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("Inputs params regType: {}. Exception:   {}", regType, e);
			return new APIResponse<String>(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@GetMapping(value = "/getNumberRangByTROrPR", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getNumberRangByTROrPR(@RequestParam(name = "trNumber") String trNumber,
			@RequestParam(name = "mobileNo") String mobileNo,
			@RequestParam(name = "isPrNo", required = false, defaultValue = "false") boolean isPrNo) {

		try {

			BidConfigMasterVO bidConfigMaster = bidDetailsService.isSpecialNumberRegistrationDurationInBetween();
			return new APIResponse<>(true, HttpStatus.OK,
					specialPremiumNumberService.getNumberRangByTROrPR(trNumber, mobileNo, bidConfigMaster, isPrNo));
		} catch (BadRequestException e) {
			logger.error("Inputs param trNumber: {}. BadRequestException:   {}", trNumber, e.getMessage());
			return new APIResponse<String>(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			logger.error("Inputs params trNumber: {}. Exception:   {}", trNumber, e);
			return new APIResponse<String>(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@PostMapping(value = "/getSnSpecialNumberDetailsBySelectedPrSeries", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> searchPaymentStatusBySelectedPrSeries(@RequestParam(name = "selectedPrSeries") String selectedPrSeries) {

		logger.debug("searchPaymentStatus for specialNumberAppId : [{}] ", selectedPrSeries);
		try {
			synchronized (selectedPrSeries.intern()) {
				List<SpecialNumberDetailsVo> result = specialPremiumNumberService.searchPaymentStatusBySelectedPrSeries(selectedPrSeries);
				return new APIResponse<List<SpecialNumberDetailsVo>>(true, HttpStatus.OK, result);
			}
		} catch (BadRequestException e) {
			logger.error("searchPaymentStatus : {}", e.getMessage());
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("searchPaymentStatus : {}", e);
			return new APIResponse<String>(HttpStatus.NOT_FOUND, e.getMessage());
		}

	}
	
}
