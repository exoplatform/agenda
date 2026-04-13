/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import './initComponents.js';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;
exoi18n.loadLanguageAsync(lang, url);

extensionRegistry.registerComponent(
  'SpaceSettings',
  'space-settings-components',
  {
    id: 'agenda-space-setting',
    vueComponent:
          Vue.options.components['agenda-space-administration'],
    rank: 1,
  }
);



