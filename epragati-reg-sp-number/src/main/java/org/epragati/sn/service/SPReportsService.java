package org.epragati.sn.service;

import org.epragati.sn.vo.SPReportOfficeInput;
import org.epragati.sn.vo.SPReportOverAllInput;
import org.epragati.sn.vo.SPReportOverall;

public interface SPReportsService {

	SPReportOverall getSPAmountReportByOffice(SPReportOfficeInput input);

	SPReportOverall getSPAmountReport(SPReportOverAllInput input);
}
