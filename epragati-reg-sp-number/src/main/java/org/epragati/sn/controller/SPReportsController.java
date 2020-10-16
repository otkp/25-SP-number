package org.epragati.sn.controller;

import org.epragati.aadhar.APIResponse;
import org.epragati.exception.BadRequestException;
import org.epragati.sn.service.SPReportsService;
import org.epragati.sn.vo.SPReportOfficeInput;
import org.epragati.sn.vo.SPReportOverAllInput;
import org.epragati.sn.vo.SPReportOverall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class SPReportsController {

	private static final Logger logger = LoggerFactory.getLogger(SPReportsController.class);

	@Autowired
	private SPReportsService spReportsService;

	@PostMapping(path = "/getSPAmountReportByOffice", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getSPAmountReportByOffice(@RequestBody @Validated SPReportOfficeInput input) {

		logger.debug("SPReportOfficeInput [{}] ", input);

		try {

			SPReportOverall result = spReportsService.getSPAmountReportByOffice(input);

			return new APIResponse<>(true, HttpStatus.OK, result);

		} catch (BadRequestException bre) {
			return new APIResponse<String>(HttpStatus.NOT_FOUND, bre.getMessage());
		}
	}
	
	@PostMapping(path = "/getSPAmountReport", produces = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE })
	public APIResponse<?> getSPAmountReport(@RequestBody @Validated SPReportOverAllInput input) {
		
		logger.debug("SPReportOfficeInput [{}] ", input);
		
		try {
			
			SPReportOverall result = spReportsService.getSPAmountReport(input);
			
			return new APIResponse<>(true, HttpStatus.OK, result);
			
		} catch (BadRequestException bre) {
			return new APIResponse<>(HttpStatus.NOT_FOUND, bre.getMessage());
		}
	}

}
