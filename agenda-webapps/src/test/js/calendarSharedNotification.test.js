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
import {shallowMount} from '@vue/test-utils';
import fs from 'fs';
import path from 'path';

import CalendarSharedPlugin from '../../main/webapp/vue-app/agenda-notifications/components/CalendarSharedPlugin.vue';

/**
 * The "a calendar was shared with you" notification as the drawer and the
 * notifications page draw it (EXO-90357): the owner's avatar, the sentence
 * naming the owner and the calendar, the calendar's name, a button to the
 * agenda, and the registrations that make the notification UI pick this
 * component and file it under Agenda.
 */
describe('Calendar shared notification', () => {

  Vue.config.ignoredElements = [/^v-/];

  const NOTIFICATION = {
    id: 107,
    plugin: 'CalendarSharedNotificationPlugin',
    read: false,
    created: '2026-09-17T12:24:16.463Z',
    from: {username: 'bob', fullname: 'Bob Builder', avatar: '/rest/v1/social/users/bob/avatar?v=1'},
    parameters: {
      calendarId: '18',
      calendarName: 'BobCal <2026>',
      ownerName: 'Bob Builder',
      ownerUsername: 'bob',
      Url: '/portal/dw/agenda',
    },
  };

  /** What the shared template received. */
  let received;

  /**
   * Mounts the component over a stub of social's notification item, which
   * records its props and draws the actions slot.
   *
   * @param {Object} notification the notification
   * @returns {Object} the wrapper
   */
  function mountPlugin(notification) {
    received = null;
    return shallowMount(CalendarSharedPlugin, {
      propsData: {notification},
      mocks: {
        $t: (key, args) => (args ? `${key}(${Object.values(args).join('|')})` : key),
      },
      stubs: {
        'user-notification-template': {
          props: ['notification', 'avatarUrl', 'message', 'url'],
          template: '<div class="template-stub"><slot name="actions"></slot></div>',
          created() {
            received = {avatarUrl: this.avatarUrl, message: this.message, url: this.url};
          },
        },
      },
    });
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {context: '/portal', rest: 'rest', metaPortalName: 'dw'}}};
  });

  it('shows the owner, the calendar, the owner\'s avatar and a button to the agenda', () => {
    const wrapper = mountPlugin(NOTIFICATION);

    expect(received.avatarUrl).toBe('/rest/v1/social/users/bob/avatar?v=1');
    expect(received.url).toBe('/portal/dw/agenda');
    expect(received.message).toBe('Notification.agenda.calendar.shared('
      + '<a class="user-name font-weight-bold">Bob Builder</a>|'
      + '<span class="font-weight-bold">BobCal &lt;2026&gt;</span>)');
    expect(wrapper.find('.agenda-calendar-shared-name').text()).toContain('BobCal <2026>');
    const button = wrapper.find('.agenda-calendar-shared-open');
    expect(button.attributes('href')).toBe('/portal/dw/agenda');
    expect(button.text()).toBe('Notification.agenda.calendar.shared.view');
  });

  it('falls back to what was stored when the sender is not carried', () => {
    const {from, ...withoutSender} = NOTIFICATION;
    expect(from).toBeDefined();
    mountPlugin({...withoutSender, parameters: {...NOTIFICATION.parameters, Url: ''}});

    expect(received.avatarUrl).toBe('/portal/rest/v1/social/users/bob/avatar');
    expect(received.url).toBe('/portal/dw/agenda');
    expect(received.message).toContain('>Bob Builder</a>');
  });

  it('is registered as the content of its plugin and filed under Agenda', () => {
    const extensions = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-notifications/extensions.js'), 'utf8');
    const components = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-notifications/initComponents.js'), 'utf8');

    const group = extensions.match(/'notification-group-extension', \{[\s\S]*?\}\);/)[0];
    expect(group).toContain('\'CalendarSharedNotificationPlugin\'');
    expect(extensions).toMatch(/type: 'CalendarSharedNotificationPlugin',\s*rank: 10,\s*vueComponent: Vue\.options\.components\['user-notification-calendar-shared'\]/);
    expect(components).toContain('\'user-notification-calendar-shared\': CalendarSharedPlugin');
  });
});
