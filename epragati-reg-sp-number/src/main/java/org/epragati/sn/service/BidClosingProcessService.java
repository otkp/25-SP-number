package org.epragati.sn.service;

import java.util.List;

public interface BidClosingProcessService {
	
	List<String> doBidClosingProcess();
	void assignPrForBidWinner();
	void refound();
	void refound(String spAppId);
}
