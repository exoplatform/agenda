// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

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
