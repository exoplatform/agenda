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
import Vue from 'vue';
import {mount} from '@vue/test-utils';

import AgendaConnectorAvatar from '../../main/webapp/vue-app/agenda-common/components/connector/AgendaConnectorAvatar.vue';

/**
 * The white tile belongs to the image, not to the avatar (EXO-90393).
 *
 * <p>Since a connector's packaged default may now be a font icon rather than a
 * packaged image, the surfaces drawing a connector on a coloured background —
 * the mobile timeline row, the calendar chip — could no longer hardcode a white
 * tile behind it: a glyph in a white square on a coloured row is a white square.
 * The rule moved into this component, which is the only place that knows which
 * of the two it is about to render.</p>
 *
 * <p><b>Harness.</b> Vuetify is not part of this build, so its tags are left as
 * plain elements; the pins are on the classes and the glyph this component
 * emits.</p>
 */
describe('The connector avatar', () => {

  Vue.config.ignoredElements = [/^v-/];

  /**
   * Mounts the avatar over a connector descriptor.
   *
   * @param {Object} connector the descriptor to render
   * @param {Object} props any other prop to pass
   * @returns {Object} the wrapper
   */
  function mountWith(connector, props) {
    return mount(AgendaConnectorAvatar, {
      propsData: Object.assign({connector: connector}, props || {}),
      mocks: {$t: key => key},
    });
  }

  it('draws no tile behind a font icon, which takes the colour it is given', () => {
    const wrapper = mountWith({name: 'agenda.caldavCalendar', icon: 'fas fa-calendar-alt'});

    expect(wrapper.classes()).not.toContain('white');
    expect(wrapper.find('v-icon').text()).toBe('fas fa-calendar-alt');
    expect(wrapper.find('img').exists()).toBe(false);
  });

  it('keeps the white tile behind an image, which is drawn for a light backdrop', () => {
    const uploaded = mountWith({name: 'agenda.caldavCalendar', icon: 'fas fa-calendar-alt', imageUrl: '/caldav/rest/servers/6/image?v=1'});

    expect(uploaded.classes()).toContain('white');
    expect(uploaded.find('img').attributes('src')).toBe('/caldav/rest/servers/6/image?v=1');

    const packaged = mountWith({name: 'agenda.googleCalendar', avatar: '/agenda/skin/images/google.png'});

    expect(packaged.classes()).toContain('white');
    expect(packaged.find('img').attributes('src')).toBe('/agenda/skin/images/google.png');
  });

  it('colours the glyph the way the surface asks, the platform default when it asks nothing', () => {
    const connector = {name: 'agenda.caldavCalendar', icon: 'fas fa-calendar-alt'};

    expect(mountWith(connector).find('v-icon').classes()).toContain('icon-default-color');
    expect(mountWith(connector, {iconClass: 'white--text'}).find('v-icon').classes()).toContain('white--text');
    expect(mountWith(connector, {iconClass: 'white--text'}).find('v-icon').classes()).not.toContain('icon-default-color');
  });

  it('lets an uploaded image win over a font icon, as the admin screens do', () => {
    const wrapper = mountWith({name: 'agenda.caldavCalendar', icon: 'fa-server', imageUrl: '/caldav/rest/servers/6/image?v=1'});

    expect(wrapper.find('v-icon').exists()).toBe(false);
    expect(wrapper.find('img').attributes('src')).toBe('/caldav/rest/servers/6/image?v=1');
  });

});
