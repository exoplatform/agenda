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
package org.exoplatform.agenda.dao;

import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import org.exoplatform.agenda.entity.RemoteProviderEntity;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;

public class RemoteProviderDAO extends GenericDAOJPAImpl<RemoteProviderEntity, Long> {

  public RemoteProviderEntity findByName(String connectorName) {
    TypedQuery<RemoteProviderEntity> query = getEntityManager().createNamedQuery("AgendaRemoteProvider.findByName",
                                                                                 RemoteProviderEntity.class);
    query.setParameter("name", connectorName);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {// NOSONAR : normal to not log this and not
                                   // rethrow it
      return null;
    }
  }

}
