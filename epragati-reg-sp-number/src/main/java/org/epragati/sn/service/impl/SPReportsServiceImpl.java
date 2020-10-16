package org.epragati.sn.service.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.epragati.sn.dao.SpecialNumberDetailsDAO;
import org.epragati.sn.dto.SpecialNumberDetailsDTO;
import org.epragati.sn.service.SPReportsService;
import org.epragati.sn.vo.SPReportDetails;
import org.epragati.sn.vo.SPReportOfficeInput;
import org.epragati.sn.vo.SPReportOverAllInput;
import org.epragati.sn.vo.SPReportOverall;
import org.epragati.util.BidNumberType;
import org.epragati.util.BidStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SPReportsServiceImpl implements SPReportsService {

	@Autowired
	private SpecialNumberDetailsDAO specialNumberDetailsDAO;

	@Override
	public SPReportOverall getSPAmountReportByOffice(SPReportOfficeInput input) {

		LocalDateTime fromDate = input.getFromDate().atStartOfDay();
		LocalDateTime toDate = input.getToDate().atTime(LocalTime.MAX);

		List<SpecialNumberDetailsDTO> participantDetails;
		SPReportOverall result = new SPReportOverall();

		// Bid winners
		participantDetails = specialNumberDetailsDAO
				.findByVehicleDetailsRtaOfficeOfficeCodeAndActionsDetailsLogActionAndCreatedDateBetween(
						input.getOfficeCode(), BidStatus.BIDWIN.getDescription(), fromDate, toDate);
		result.setWinnerDetails(getWinnerSPReport(participantDetails));

		// Bid looses
		participantDetails = specialNumberDetailsDAO
				.findByVehicleDetailsRtaOfficeOfficeCodeAndActionsDetailsLogActionAndCreatedDateBetween(
						input.getOfficeCode(), BidStatus.BIDLOOSE.getDescription(), fromDate, toDate);
		result.setLooserDetails(getLooserSPReport(participantDetails));

		// Bid absent
		participantDetails = specialNumberDetailsDAO
				.findByVehicleDetailsRtaOfficeOfficeCodeAndActionsDetailsLogActionAndCreatedDateBetween(
						input.getOfficeCode(), BidStatus.BIDABSENT.getDescription(), fromDate, toDate);
		result.setAbsentDetails(getAbsentSPReport(participantDetails));

		// Bid tie
		participantDetails = specialNumberDetailsDAO
				.findByVehicleDetailsRtaOfficeOfficeCodeAndActionsDetailsLogActionAndCreatedDateBetween(
						input.getOfficeCode(), BidStatus.BIDTIE.getDescription(), fromDate, toDate);
		result.setTieDetails(getLooserSPReport(participantDetails));

		return result;

	}

	@Override
	public SPReportOverall getSPAmountReport(SPReportOverAllInput input) {

		LocalDateTime fromDate = input.getFromDate().atStartOfDay();
		LocalDateTime toDate = input.getToDate().atTime(LocalTime.MAX);

		List<SpecialNumberDetailsDTO> participantDetails;
		SPReportOverall result = new SPReportOverall();

		// Bid winners
		participantDetails = specialNumberDetailsDAO.findByActionsDetailsLogActionAndCreatedDateBetween(
				BidStatus.BIDWIN.getDescription(), fromDate, toDate);
		result.setWinnerDetails(getWinnerSPReport(participantDetails));

		// Bid looses
		participantDetails = specialNumberDetailsDAO.findByActionsDetailsLogActionAndCreatedDateBetween(
				BidStatus.BIDLOOSE.getDescription(), fromDate, toDate);
		result.setLooserDetails(getLooserSPReport(participantDetails));

		// Bid absent
		participantDetails = specialNumberDetailsDAO.findByActionsDetailsLogActionAndCreatedDateBetween(
				BidStatus.BIDABSENT.getDescription(), fromDate, toDate);
		result.setAbsentDetails(getAbsentSPReport(participantDetails));

		// Bid tie
		participantDetails = specialNumberDetailsDAO.findByActionsDetailsLogActionAndCreatedDateBetween(
				BidStatus.BIDTIE.getDescription(), fromDate, toDate);
		result.setTieDetails(getLooserSPReport(participantDetails));

		return result;
	}

	private SPReportDetails getWinnerSPReport(List<SpecialNumberDetailsDTO> participantDetails) {
		double specialAmount = 0d;
		double premiumAmount = 0d;
		long specialParticipants = 0l;
		long premiumParticipants = 0l;

		for (SpecialNumberDetailsDTO participant : participantDetails) {

			double totalAmount = participant.getSpecialNumberFeeDetails().getTotalAmount()
					+ participant.getBidFinalDetails().getTotalAmount();
			if (BidNumberType.P.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				premiumAmount += totalAmount;
				premiumParticipants++;

			} else if (BidNumberType.S.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				specialAmount += totalAmount;
				specialParticipants++;
			}
		}

		SPReportDetails report = new SPReportDetails();
		report.setPremiumAmount(premiumAmount);
		report.setSpecialAmount(specialAmount);
		report.setPremiumParticipants(premiumParticipants);
		report.setSpecialParticipants(specialParticipants);
		return report;
	}

	private SPReportDetails getLooserSPReport(List<SpecialNumberDetailsDTO> participantDetails) {
		double specialAmount = 0d;
		double premiumAmount = 0d;
		long specialParticipants = 0l;
		long premiumParticipants = 0l;

		for (SpecialNumberDetailsDTO participant : participantDetails) {

			double totalAmount = participant.getSpecialNumberFeeDetails().getBidFeeMaster().getRtaBidFee()
					+ participant.getBidFinalDetails().getRtaAmount();

			if (BidNumberType.P.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				premiumAmount += totalAmount;
				premiumParticipants++;

			} else if (BidNumberType.S.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				specialAmount += totalAmount;
				specialParticipants++;
			}
		}

		SPReportDetails report = new SPReportDetails();
		report.setPremiumAmount(premiumAmount);
		report.setSpecialAmount(specialAmount);
		report.setPremiumParticipants(premiumParticipants);
		report.setSpecialParticipants(specialParticipants);
		return report;
	}

	private SPReportDetails getAbsentSPReport(List<SpecialNumberDetailsDTO> participantDetails) {
		double specialAmount = 0d;
		double premiumAmount = 0d;
		long specialParticipants = 0l;
		long premiumParticipants = 0l;

		for (SpecialNumberDetailsDTO participant : participantDetails) {

			double totalAmount = participant.getSpecialNumberFeeDetails().getTotalAmount();

			if (participant.getBidFinalDetails() != null) {
				totalAmount += participant.getBidFinalDetails().getTotalAmount();
			}
			if (BidNumberType.P.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				premiumAmount += totalAmount;
				premiumParticipants++;

			} else if (BidNumberType.S.getCode()
					.equalsIgnoreCase(participant.getBidVehicleDetails().getAllocatedBidNumberType().getCode())) {

				specialAmount += totalAmount;
				specialParticipants++;
			}
		}

		SPReportDetails report = new SPReportDetails();
		report.setPremiumAmount(premiumAmount);
		report.setSpecialAmount(specialAmount);
		report.setPremiumParticipants(premiumParticipants);
		report.setSpecialParticipants(specialParticipants);
		return report;
	}

}
