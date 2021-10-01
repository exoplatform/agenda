// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

export function initCometd(cometdContext, cometdToken, callback) {
  const loc = window.location;
  cCometd.configure({
    url: `${loc.protocol}//${loc.hostname}${(loc.port && ':') || ''}${loc.port || ''}/${cometdContext}/cometd`,
    exoId: eXo.env.portal.userName,
    exoToken: cometdToken,
  });

  cCometd.subscribe('/eXo/Application/Addons/Agenda', null, (event) => {
    const data = event.data && JSON.parse(event.data);
    if (!data) {
      return;
    }
    callback(data.wsEventName, data.params && data.params.agendaeventmodification || data.params);
  });
}