package org.epragati.sn.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.epragati.exception.BadRequestException;
import org.epragati.master.service.PrSeriesService;
import org.epragati.service.notification.MessageTemplate;
import org.epragati.service.notification.NotificationUtil;
import org.epragati.sn.dao.BidConfigMasterDAO;
import org.epragati.sn.dao.SpecialNumberDetailsDAO;
import org.epragati.sn.dto.BidConfigMaster;
import org.epragati.sn.dto.SpecialNumberDetailsDTO;
import org.epragati.sn.numberseries.dao.PRPoolDAO;
import org.epragati.sn.numberseries.dto.BidParticipantsDto;
import org.epragati.sn.numberseries.dto.BidParticipantsLogDTO;
import org.epragati.sn.numberseries.dto.PRPoolDTO;
import org.epragati.sn.payment.service.SnPaymentGatewayService;
import org.epragati.sn.service.BidClosingProcessService;
import org.epragati.sn.service.SpecialPremiumNumberService;
import org.epragati.sn.vo.BidConfigMasterVO;
import org.epragati.sn.vo.BidParticipantsResult;
import org.epragati.util.BidStatus;
import org.epragati.util.NumberPoolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BidClosingProcessServiceImpl implements BidClosingProcessService {

	private static final Logger logger = LoggerFactory.getLogger(BidClosingProcessServiceImpl.class);

	@Autowired
	private PRPoolDAO numbersPoolDAO;

	@Autowired
	private SpecialNumberDetailsDAO specialNumberDetailsDAO;

	@Autowired
	private ActionsDetailsHelper actionsDetailsHelper;

	@Autowired
	private BidConfigMasterDAO bidConfigMasterDAO;

	@Autowired
	private SnPaymentGatewayService snPaymentGatewayService;

	private String schedulerUser = "Scheduler";

	@Autowired
	private NotificationUtil notifications;

	@Autowired
	private PrSeriesService prSeriesService;

	@Autowired
	private SpecialPremiumNumberService specialPremiumNumberService;

	private List<String> errors;

	private BidConfigMaster bidConfigMaster;

	@Override
	public List<String> doBidClosingProcess() {
		logger.info("Start BidClosingProcess");
		errors = new ArrayList<>();
		Optional<BidConfigMaster> bidConfigMasterOptional = bidConfigMasterDAO.findByConfigStatus("Active");
		bidConfigMaster = bidConfigMasterOptional.get();
		updateBidMaster(Boolean.TRUE, bidConfigMaster);
		List<PRPoolDTO> bidOpenDetails = numbersPoolDAO
				.findByBidProcessStatusAndBidParticipantsNotNull(NumberPoolStatus.BidProcessStatus.OPEN);

		bidOpenDetails.stream().forEach(this::doBidClosingProcess);
		// Handling SPPAYMENTFAILURE and SPPAYMENTPENDING Records
		bidOpenDetails.clear();
		handlingPaymetInitiatedRecords();

		doRefundPaymentProcess();
		updateBidMaster(Boolean.FALSE, bidConfigMaster);

		logger.info("End BidClosingProcess");
		return errors;
	}

	private void updateBidMaster(boolean isRunningBidClosingProcess, BidConfigMaster bidConfigMaster) {
		bidConfigMaster.setRunningBidClosingProcess(isRunningBidClosingProcess);
		bidConfigMasterDAO.save(bidConfigMaster);
	}

	private void handlingPaymetInitiatedRecords() {
		logger.info("handling Paymet Initiated Records Start");
		List<SpecialNumberDetailsDTO> bidNotStartedRecords = specialNumberDetailsDAO
				.findByBidStatusIn(Arrays.asList(BidStatus.SPPAYMENTFAILURE, BidStatus.SPPAYMENTPENDING));
		bidNotStartedRecords.parallelStream().forEach(p -> {
			p.setBidStatus(BidStatus.SPPAYMENTFAILED);
			actionsDetailsHelper.updateActionsDetails(p, schedulerUser,
					"Special number selection initiated but not paid", BidStatus.SPPAYMENTFAILED.getDescription());

		});
		specialNumberDetailsDAO.save(bidNotStartedRecords);
		logger.info("handling Paymet Initiated Records End");
	}

	private void doRefundPaymentProcess() {
		logger.info("Refund Paymen tProcess Start");
		LocalDateTime minDateFrom = LocalDateTime.now().minusDays(6);

		// List<SpecialNumberDetailsDTO> bidders =
		// specialNumberDetailsDAO.findByBidStatusInAndCreatedDateGreaterThan(refundBidStatuses,minDateFrom);
		List<SpecialNumberDetailsDTO> bidders = specialNumberDetailsDAO
				.findByBidStatusInAndSpecialNumberFeeDetailsIsRefundDoneAndCreatedDateGreaterThan(
						bidConfigMaster.getRefundStatuses(), false, minDateFrom);
		bidders.stream().forEach(this::doRefundPaymentProcess);
		bidders.clear();
		bidders = specialNumberDetailsDAO.findByBidStatusInAndBidFinalDetailsIsRefundDoneAndCreatedDateGreaterThan(
				bidConfigMaster.getRefundStatuses(), false, minDateFrom);
		bidders.stream().forEach(this::doRefundPaymentProcess);
		bidders.clear();

		logger.info("Refund Paymen tProcess END");
	}

	private void doRefundPaymentProcess(SpecialNumberDetailsDTO bidder) {
		try {
			if (BidStatus.BIDWIN.equals(bidder.getBidStatus()) || (BidStatus.BIDABSENT.equals(bidder.getBidStatus())
					&& bidConfigMaster.getRefundForAbsentStartFrom().isAfter(bidder.getCreatedDate()))) {
				return;
			}

			if (null != bidder.getSpecialNumberFeeDetails()
					&& StringUtils.isNoneBlank(bidder.getSpecialNumberFeeDetails().getPaymentId())
					&& !bidder.getSpecialNumberFeeDetails().getIsRefundDone()) {

				Optional<String> refundIdOptional = doPaymentRefundProcess(
						bidder.getSpecialNumberFeeDetails().getTransactionNo(),
						bidder.getSpecialNumberFeeDetails().getPaymentId(),
						bidder.getSpecialNumberFeeDetails().getApplicationAmount());
				if (refundIdOptional.isPresent()) {
					bidder.getSpecialNumberFeeDetails().setRefundId(refundIdOptional.get());
					actionsDetailsHelper.updateActionsDetails(bidder, schedulerUser,
							"Refund Id:" + refundIdOptional.get(), "Special number registration refund.");
					bidder.getSpecialNumberFeeDetails().setIsRefundDone(Boolean.TRUE);
				}
			}

			if (bidder.getBidFinalDetails() != null
					&& StringUtils.isNotBlank(bidder.getBidFinalDetails().getPaymentId())
					&& !bidder.getBidFinalDetails().getIsRefundDone()) {

				Optional<String> refundIdOptional = doPaymentRefundProcess(
						bidder.getBidFinalDetails().getTransactionNo(), bidder.getBidFinalDetails().getPaymentId(),
						bidder.getBidFinalDetails().getBidAmount());
				if (refundIdOptional.isPresent()) {
					bidder.getBidFinalDetails().setRefundId(refundIdOptional.get());
					actionsDetailsHelper.updateActionsDetails(bidder, schedulerUser,
							"Refund Id:" + refundIdOptional.get(), "Final bid refund.");
					bidder.getBidFinalDetails().setIsRefundDone(Boolean.TRUE);
				}
			}
			specialNumberDetailsDAO.save(bidder);
		} catch (Exception e) {
			errors.add(e.getMessage());
			logger.error("{}", e);
		}
	}

	private Optional<String> doPaymentRefundProcess(String transactionNo, String paymentId, Double refundAmount) {
		try {
			return snPaymentGatewayService.processRefundByPaymentId(transactionNo, paymentId, refundAmount);

		} catch (Exception e) {
			logger.error("Exception while payU refound.{}", e);
		}
		return Optional.empty();
	}

	private void doBidClosingProcess(PRPoolDTO prPoolDTO) {
		try {

			logger.info("Number pool ID: {} ", prPoolDTO.getNumberPoolId());

			BidParticipantsResult bidParticipantsResult = getParticipantsResult(prPoolDTO);

			if (bidParticipantsResult.getWinnerParticipantDetails().size() == 1) {
				// Winner is only one
				doBidLosserProcess(prPoolDTO, bidParticipantsResult.getLosserParticipantDetails());

				specialPremiumNumberService.doBidWinnerProcess(prPoolDTO,
						bidParticipantsResult.getWinnerParticipantDetails().get(0));

			} else if (bidParticipantsResult.getWinnerParticipantDetails().size() > 1) {
				// Winner is more than one
				doBidTieProcess(prPoolDTO, bidParticipantsResult.getWinnerParticipantDetails());
				doBidLosserProcess(prPoolDTO, bidParticipantsResult.getLosserParticipantDetails());
				doBidNumberCancel(prPoolDTO, prPoolDTO.getBidParticipants());

			} else {
				// No Winner
				doBidLosserProcess(prPoolDTO, bidParticipantsResult.getLosserParticipantDetails());
				doBidNumberCancel(prPoolDTO, prPoolDTO.getBidParticipants());
			}

			doBidOthersProcess(prPoolDTO, bidParticipantsResult.getOthersParticipantDetails());
		} catch (Exception e) {
			errors.add(e.getMessage());
			logger.error("Exception while run bid clouser {}", e);
		}

	}

	private BidParticipantsResult getParticipantsResult(PRPoolDTO bidNumberDetail) {

		List<String> participantIds = bidNumberDetail.getBidParticipants().stream()
				.map(BidParticipantsDto::getSpecialNumberAppId).collect(Collectors.toList());

		List<SpecialNumberDetailsDTO> participantDetails = specialNumberDetailsDAO
				.findBySpecialNumberAppIdIn(participantIds);

		/*
		 * for Showing final Bid Amount After Bid Close.--upMap
		 */
		participantDetails.forEach(specialNumberDetailsDTO -> {
			if (null != specialNumberDetailsDTO.getBidFinalDetails()) {
				specialNumberDetailsDTO.getBidFinalDetails()
						.setBidAmountNumber(specialNumberDetailsDTO.getBidFinalDetails().getBidAmount());
			}
		});

		Double maxAmount = 0d;
		if (participantDetails.stream().anyMatch(p -> p.getBidStatus().equals(BidStatus.FINALPAYMENTDONE))) {
			maxAmount = participantDetails.stream()
					.collect(Collectors.maxBy(Comparator.comparing(SpecialNumberDetailsDTO::getMaxBidAmount))).get()
					.getBidFinalDetails().getTotalAmount();
			return devideFinalPayiesParticipants(maxAmount, participantDetails);
		} else if (participantDetails.stream()
				.anyMatch(p -> Arrays
						.asList(BidStatus.SPPAYMENTDONE, BidStatus.FINALPAYMENTFAILURE, BidStatus.FINALPAYMENTPENDING)
						.contains(p.getBidStatus()))) {
			maxAmount = participantDetails.stream()
					.collect(Collectors.maxBy(Comparator.comparing(SpecialNumberDetailsDTO::getMaxSPNumberAmount)))
					.get().getSpecialNumberFeeDetails().getApplicationAmount();
			return devideSPPayiesParticipants(maxAmount, participantDetails);
		} else {

			return devideSPPayiesParticipants(null, participantDetails);
		}

	}

	private BidParticipantsResult devideSPPayiesParticipants(Double maxAmount,
			List<SpecialNumberDetailsDTO> participantDetails) {

		List<SpecialNumberDetailsDTO> loserParticipantDetails = new ArrayList<>();
		List<SpecialNumberDetailsDTO> winnerParticipantDetails = new ArrayList<>();
		List<SpecialNumberDetailsDTO> othersParticipantDetails = new ArrayList<>();

		if (maxAmount == null) {
			// if No one paid at least sppayment but payment initiated.
			participantDetails.stream().forEach(p -> {
				p.setBidStatus(BidStatus.BIDABSENT);
				actionsDetailsHelper.updateActionsDetails(p, schedulerUser);

			});
			return new BidParticipantsResult(loserParticipantDetails, winnerParticipantDetails, participantDetails);
		}
		participantDetails.stream().forEach(participant -> {
			if (participant.getSpecialNumberFeeDetails().getApplicationAmount().equals(maxAmount)) {
				winnerParticipantDetails.add(participant);
			} else {
				actionsDetailsHelper.updateActionsDetails(participant, schedulerUser);
				participant.setBidStatus(BidStatus.BIDABSENT);
				othersParticipantDetails.add(participant);
			}

		});
		return new BidParticipantsResult(loserParticipantDetails, winnerParticipantDetails, othersParticipantDetails);

	}

	private BidParticipantsResult devideFinalPayiesParticipants(Double maxAmount,
			List<SpecialNumberDetailsDTO> participantDetails) {

		List<SpecialNumberDetailsDTO> loserParticipantDetails = new ArrayList<>();
		List<SpecialNumberDetailsDTO> winnerParticipantDetails = new ArrayList<>();
		List<SpecialNumberDetailsDTO> othersParticipantDetails = new ArrayList<>();

		participantDetails.stream().forEach(participant -> {
			if (participant.getBidFinalDetails() == null
					|| BidStatus.FINALPAYMENTFAILURE.equals(participant.getBidStatus())
					|| BidStatus.FINALPAYMENTPENDING.equals(participant.getBidStatus())
					|| BidStatus.SPPAYMENTDONE.equals(participant.getBidStatus())) {
				participant.setBidStatus(BidStatus.BIDABSENT);
				actionsDetailsHelper.updateActionsDetails(participant, schedulerUser);
				othersParticipantDetails.add(participant);
			} else if (participant.getBidFinalDetails().getTotalAmount().equals(maxAmount)) {
				winnerParticipantDetails.add(participant);
			} else {
				loserParticipantDetails.add(participant);
			}

		});
		return new BidParticipantsResult(loserParticipantDetails, winnerParticipantDetails, othersParticipantDetails);
	}

	private void doBidOthersProcess(PRPoolDTO bidNumberDetail, List<SpecialNumberDetailsDTO> participantDetails) {

		if (participantDetails == null || participantDetails.isEmpty()) {
			return;
		}

		specialNumberDetailsDAO.save(participantDetails);
	}

	private void doBidLosserProcess(PRPoolDTO bidNumberDetail, List<SpecialNumberDetailsDTO> losserParticipantDetails) {

		doBidLosserOrTieProcess(bidNumberDetail, losserParticipantDetails, BidStatus.BIDLOOSE);

	}

	private void doBidLosserOrTieProcess(PRPoolDTO bidNumberDetail,
			List<SpecialNumberDetailsDTO> losserParticipantDetails, BidStatus status) {

		if (losserParticipantDetails.isEmpty()) {
			return;
		}

		Long bidMaxIteration = getBidMaxIteration();

		losserParticipantDetails.stream().forEach(participant -> {

			participant.setBidStatus(status);
			actionsDetailsHelper.updateActionsDetails(participant, schedulerUser);

			if (participant.getBidIteration() > bidMaxIteration) {
				participant.setBidStatus(BidStatus.BIDLIMITEXCEED);
				actionsDetailsHelper.updateActionsDetails(participant, schedulerUser);
			}
			Integer templateId = getTemplateIDBaseOnBitStatus(participant.getBidStatus());
			notifications.sendNotifications(templateId, participant);
		});

		specialNumberDetailsDAO.save(losserParticipantDetails);
	}

	private Integer getTemplateIDBaseOnBitStatus(BidStatus bidStatus) {

		if (BidStatus.BIDLOOSE.equals(bidStatus)) {
			return MessageTemplate.SP_BID_LOOSE.getId();
		}
		if (BidStatus.BIDTIE.equals(bidStatus)) {
			return MessageTemplate.SP_BID_TIE.getId();
		}
		if (BidStatus.BIDLIMITEXCEED.equals(bidStatus)) {
			return MessageTemplate.SP_BID_LIMITEXCEED.getId();
		}
		return null;
	}

	private void doBidTieProcess(PRPoolDTO prPoolDTO, List<SpecialNumberDetailsDTO> losserParticipantDetails) {

		doBidLosserOrTieProcess(prPoolDTO, losserParticipantDetails, BidStatus.BIDTIE);
	}

	private Long getBidMaxIteration() {
		Optional<BidConfigMasterVO> bidConfigMasterOptional = prSeriesService.getBidConfigMasterData(Boolean.TRUE);
		if (!bidConfigMasterOptional.isPresent()) {
			throw new BadRequestException("No Active Bid Config master found.");
		}

		return bidConfigMasterOptional.get().getBidMaxIteration();
	}

	private void doBidNumberCancel(PRPoolDTO prPoolDTO, List<BidParticipantsDto> bidParticipants) {

		if (!NumberPoolStatus.LEFTOVER.equals(prPoolDTO.getPoolStatus())) {
			prPoolDTO.setPoolStatus(NumberPoolStatus.OPEN);
		}
		prPoolDTO.setReservedDate(null);
		prPoolDTO.setBidProcessStatus(NumberPoolStatus.BidProcessStatus.CANCELED);

		BidParticipantsLogDTO bidParticipantsLogDTO = new BidParticipantsLogDTO();
		bidParticipantsLogDTO.setBidCancledDate(LocalDateTime.now());
		bidParticipantsLogDTO.setBidParticipants(bidParticipants);
		if (null == prPoolDTO.getBidParticipantsLogs()) {
			prPoolDTO.setBidParticipantsLogs(new ArrayList<>());
		}
		prPoolDTO.getBidParticipantsLogs().add(bidParticipantsLogDTO);
		prPoolDTO.setBidParticipants(null);

		actionsDetailsHelper.updateActionsDetails(prPoolDTO, schedulerUser);
		numbersPoolDAO.save(prPoolDTO);
	}

	@Override
	public void assignPrForBidWinner() {
//		logger.info("assignPrForBidWinner Start");
//		List<OfficeDTO> officeList=officeDAO.findAll();
//		officeList.stream().forEach(office->{
//			logger.info("Office Code {}: Start",office.getOfficeCode());
//
//			List<StagingRegistrationDetailsDTO> stagingRegistrationDetails = stagingRegistrationDetailsDAO.findByOfficeDetailsOfficeCodeAndApplicationStatus(office.getOfficeCode(), StatusRegistration.SPECIALNOPENDING.getDescription());
//			Map<String, List<SpecialNumberDetailsDTO>> seriesMap = new HashMap<>();
//			stagingRegistrationDetails.forEach(stagingReg->{
//				List<SpecialNumberDetailsDTO> spDetailsList= specialNumberDetailsDAO.findByVehicleDetailsApplicationNumberAndBidStatus(stagingReg.getApplicationNo(),BidStatus.BIDWIN);
//				if(spDetailsList.size()>1) {
//					//Double checking .
//					logger.warn("SpecialNumberDetailsDTO contains more than two times bid winstatus Staging application Numbers are:{} ",stagingReg.getApplicationNo());
//				}else {
//					spDetailsList.stream().forEach(specialNoDetail->{
//						Optional<BidNumbersDetailsDto> bidNumbersDetailsDto=bidNumbersDetailsDAO.findByBidParticipantsSpecialNumberAppId(specialNoDetail.getSpecialNumberAppId());
//						if(bidNumbersDetailsDto.isPresent()) {
//							String key= bidNumbersDetailsDto.get().getOfficeCode()+bidNumbersDetailsDto.get().getPrSeries()+bidNumbersDetailsDto.get().getPrNumber();
//
//							if(seriesMap.containsKey(key)) {//Duplicate record 
//								logger.warn("duplicate number pool record after bidwin,  Special number details _id:\"{}\"",specialNoDetail.getSpecialNumberAppId());
//								seriesMap.get(key).add(specialNoDetail);
//							}else {
//								List<SpecialNumberDetailsDTO> nonAssignedRecords=new ArrayList<>();
//								nonAssignedRecords.add(specialNoDetail);
//								seriesMap.put(key, nonAssignedRecords);
//							}
//						}else {
//							logger.warn("BidPartispent details not found for Special number details _id:\"{}\"",specialNoDetail.getSpecialNumberAppId());
//						}
//
//					});
//				}
//
//			});
//			//logger.info("{}",seriesMap);			
//			seriesMap.entrySet().stream().forEach(entry->{
//				if(entry.getValue().size()==1) {
//					SpecialNumberDetailsDTO spNoDetailDto=entry.getValue().get(0);
//					try {
//					String prSeries= entry.getKey().replace(office.getOfficeCode(), StringUtils.EMPTY);
//					prSeries= prSeries.replace(spNoDetailDto.getSelectedNo()+StringUtils.EMPTY, StringUtils.EMPTY);
//					vehicleDetailsService.generatePRNumber(spNoDetailDto,prSeries);
//					}catch(Exception e) {
//						logger.error("special no _Id:\"{}\", eception:{}",spNoDetailDto.getSpecialNumberAppId(),e.getMessage());						
//					}
//				}else {
//					entry.getValue().stream().forEach(s->{
//						logger.warn("unassigned bid win special numbers _id\"{}\"",s.getSpecialNumberAppId());
//					});
//				}
//				
//				
//			});
//			logger.info("Office Code {}: END",office.getOfficeCode());
//
//		});
//		logger.info("assignPrForBidWinner END");
	}

	public List<String> getErrors() {
		return errors;
	}

	@Override
	public void refound() {
		// doRefundPaymentProcess();
	}

	@Override
	public void refound(String specialNoAppId) {

//		doRefundPaymentProcess();
//		updateBidMaster(Boolean.FALSE);
	}

}
