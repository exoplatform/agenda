// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.entity;

import java.io.Serializable;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

@Entity(name = "AgendaRemoteProvider")
@ExoEntity
@Table(name = "EXO_AGENDA_REMOTE_PROVIDER")
@NamedQueries(
  {
      @NamedQuery(
          name = "AgendaRemoteProvider.findByName",
          query = "SELECT rc FROM AgendaRemoteProvider rc WHERE rc.name = :name"
      ),
  }
)
public class RemoteProviderEntity implements Serializable {

  private static final long serialVersionUID = -5031970577705728288L;

  @Id
  @SequenceGenerator(name = "SEQ_AGENDA_PROVIDER_ID", sequenceName = "SEQ_AGENDA_PROVIDER_ID")
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_PROVIDER_ID")
  @Column(name = "AGENDA_PROVIDER_ID")
  private Long              id;

  @Column(name = "NAME", nullable = false)
  private String            name;

  @Column(name = "CLIENT_API")
  private String            apiKey;

  @Column(name = "ENABLED")
  private boolean           enabled;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
