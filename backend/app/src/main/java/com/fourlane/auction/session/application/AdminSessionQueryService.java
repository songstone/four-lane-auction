package com.fourlane.auction.session.application;
import com.fourlane.auction.session.domain.*;
import com.fourlane.global.error.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AdminSessionQueryService {
    private final AdminSessionCatalog catalog;
    public AdminSessionQueryService(AdminSessionCatalog catalog){this.catalog=catalog;}
    @Transactional(readOnly=true)
    public AdminSessionView get(long id){
        var h=catalog.header(id).orElseThrow(()->new ApiException(ErrorCode.SESSION_NOT_FOUND));
        var grouped=new LinkedHashMap<Long,List<AdminSessionCatalog.Row>>();
        for(var row:catalog.rows(id))grouped.computeIfAbsent(row.getLaneId(),ignored->new ArrayList<>()).add(row);
        var lanes=grouped.values().stream().map(rows->{var l=rows.getFirst();
            return new AdminSessionView.Lane(l.getLaneId(),l.getLaneNo(),l.getLaneStatus(),rows.stream().filter(r->r.getLotId()!=null)
                .map(r->new AdminSessionView.Lot(r.getLotId(),r.getSeqNo(),r.getItemId(),r.getName(),
                    r.getStorageKey()==null?null:"/api/images/"+r.getStorageKey(),r.getStartPrice(),r.getSellerNickname(),r.getStatus())).toList());}).toList();
        var empty=lanes.stream().filter(l->l.lots().isEmpty()).map(AdminSessionView.Lane::laneNo).toList();
        var timing=new AdminSessionView.Timing(h.getFirstBidWaitMs(),h.getSoftCloseMs(),h.getLotGapMs(),h.getResumeMinMs());
        return new AdminSessionView(new AdminSessionView.Session(h.getSessionId(),h.getStatus(),h.getLaneCount(),h.getScheduledStartAt(),
            h.getStartedAt(),h.getFinishedAt(),timing,h.getBidPolicyVersion()),h.getStatus()==SessionStatus.SCHEDULED && lanes.size()==h.getLaneCount() && empty.isEmpty(),empty,lanes);
    }
}
