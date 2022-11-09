/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
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
  @SequenceGenerator(name = "SEQ_AGENDA_PROVIDER_ID", sequenceName = "SEQ_AGENDA_PROVIDER_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_AGENDA_PROVIDER_ID")
  @Column(name = "AGENDA_PROVIDER_ID")
  private Long              id;

  @Column(name = "NAME", nullable = false)
  private String            name;

  @Column(name = "CLIENT_API")
  private String            apiKey;

  @Column(name = "SECRET_KEY")
  private String            secretKey;
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

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }
}
