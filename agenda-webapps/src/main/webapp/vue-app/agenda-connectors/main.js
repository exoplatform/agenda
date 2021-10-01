// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import googleConnector from './agendaGoogleConnector.js';
import officeConnector from './agendaOfficeConnector.js';

extensionRegistry.registerExtension('agenda', 'connectors', googleConnector);
extensionRegistry.registerExtension('agenda', 'connectors', officeConnector);

document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));
