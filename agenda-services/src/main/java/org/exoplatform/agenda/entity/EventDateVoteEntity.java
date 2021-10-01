// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaEventDateVote")
@ExoEntity
@Table(name = "EXO_AGENDA_EVENT_DATE_VOTE")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaEventDateVote.deleteVotesByOptionId",
          query = "DELETE from AgendaEventDateVote dateVote WHERE dateVote.dateOptionId = :dateOptionId"
      ),
      @NamedQuery(
          name = "AgendaEventDateVote.deleteVotesByOptionIds",
          query = "DELETE from AgendaEventDateVote dateVote WHERE dateVote.dateOptionId in (:dateOptionIds)"
      ),
      @NamedQuery(
          name = "AgendaEventDateVote.findVotersByDateOptionId",
          query = "SELECT dateVote from AgendaEventDateVote dateVote WHERE dateVote.dateOptionId = :dateOptionId"
      ),
      @NamedQuery(
          name = "AgendaEventDateVote.findVoteByOptionAndIdentity",
          query = "SELECT dateVote from AgendaEventDateVote dateVote WHERE dateVote.dateOptionId = :dateOptionId AND dateVote.identityId = :identityId"
      ),
  }
)
public class EventDateVoteEntity implements Serializable {

  private static final long serialVersionUID = -6015331866476045556L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_EVENT_DATE_VOTE_ID", sequenceName = "SEQ_AGENDA_EVENT_DATE_VOTE_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_EVENT_DATE_VOTE_ID")
  @Column(name = "EVENT_DATE_VOTE_ID")
  private Long              id;

  @Column(name = "EVENT_DATE_OPTION_ID", nullable = false)
  private Long              dateOptionId;

  @Column(name = "IDENTITY_ID", nullable = false)
  private Long              identityId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getDateOptionId() {
    return dateOptionId;
  }

  public void setDateOptionId(Long dateOptionId) {
    this.dateOptionId = dateOptionId;
  }

  public Long getIdentityId() {
    return identityId;
  }

  public void setIdentityId(Long identityId) {
    this.identityId = identityId;
  }

}
