package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ChangeLogRepository;
import com.trip.adaptive.repository.DiscussionPostRepository;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.NodeNoteRepository;
import com.trip.adaptive.repository.TripExpenseRepository;
import com.trip.adaptive.repository.TripRepository;

/**
 * 行程删除：行程被大量监测/决策数据引用（影响、事件、方案、变更记录、费用、讨论、节点备注）， 直接删除行程会触发外键约束失败，因此这里按引用顺序逐层清理后再删除行程本身。
 *
 * <p>节点与路线由 {@link Trip} 的级联关系随行程一起删除。
 */
@Service
public class TripDeletionService {
  private final TripRepository trips;
  private final ChangeLogRepository changeLogs;
  private final AlternativePlanRepository plans;
  private final ImpactAssessmentRepository impacts;
  private final ExternalEventRepository events;
  private final TripExpenseRepository expenses;
  private final DiscussionPostRepository discussions;
  private final NodeNoteRepository notes;

  public TripDeletionService(
      TripRepository trips,
      ChangeLogRepository changeLogs,
      AlternativePlanRepository plans,
      ImpactAssessmentRepository impacts,
      ExternalEventRepository events,
      TripExpenseRepository expenses,
      DiscussionPostRepository discussions,
      NodeNoteRepository notes) {
    this.trips = trips;
    this.changeLogs = changeLogs;
    this.plans = plans;
    this.impacts = impacts;
    this.events = events;
    this.expenses = expenses;
    this.discussions = discussions;
    this.notes = notes;
  }

  @Transactional
  public void delete(Trip trip) {
    Long id = trip.getId();
    changeLogs.deleteAll(changeLogs.findByTripId(id));
    plans.deleteAll(plans.findByTripId(id)); // 级联删除方案下的节点变更与投票
    impacts.deleteAll(impacts.findByTripId(id));
    events.deleteAll(events.findByTripId(id));
    expenses.deleteAll(expenses.findByTripIdOrderByCreatedAtDesc(id));
    discussions.deleteAll(discussions.findByTripIdOrderByCreatedAtDesc(id));
    List<ItineraryNode> nodes = trip.getItineraryNodes();
    for (ItineraryNode node : nodes) {
      notes.deleteAll(notes.findByNodeIdOrderByCreatedAtAsc(node.getId()));
    }
    trips.delete(trip);
  }
}
