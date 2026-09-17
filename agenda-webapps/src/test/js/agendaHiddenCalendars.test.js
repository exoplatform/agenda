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
import fs from 'fs';
import path from 'path';

import Agenda from '../../main/webapp/vue-app/agenda/components/Agenda.vue';

/**
 * The grid hides a subscribed calendar the user unticked (EXO-90278) the way it
 * hides a personal one: both lists feed one set of hidden calendars of the
 * user's own, filtered locally and sent to the server.
 *
 * <p>Agenda.vue is too heavy to mount here, so its computed property and methods
 * are called on a plain context holding the data they read.</p>
 */
describe('Hidden calendars of the agenda grid', () => {

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1'}}};
  });

  beforeEach(() => {
    localStorage.clear();
  });

  it('hides the events of a hidden subscribed calendar with those of a hidden personal one, never a space calendar', () => {
    const context = {
      events: [
        {id: 1, calendar: {id: 10, owner: {id: 1}}},
        {id: 2, calendar: {id: 77, owner: {id: 1}}},
        {id: 3, calendar: {id: 11, owner: {id: 1}}},
        {id: 4, calendar: {id: 77, owner: {id: 100}}},
      ],
      remoteEvents: [],
      hiddenRemoteCalendarIds: [],
      hiddenPersonalCalendarIds: [10],
      hiddenSubscribedCalendarIds: [77],
    };
    context.hiddenOwnCalendarIds = Agenda.computed.hiddenOwnCalendarIds.call(context);

    Agenda.methods.updateDisplayedEvents.call(context);

    expect(context.hiddenOwnCalendarIds).toEqual([10, 77]);
    expect(context.displayedEvent.map(event => event.id)).toEqual([3, 4]);
  });

  it('restores the hidden subscribed calendars of the user and applies a new selection', () => {
    localStorage.setItem('agenda.hiddenSubscribedCalendars.1', JSON.stringify(['77', 78]));
    const context = {hiddenSubscribedCalendarIds: []};

    Agenda.methods.initHiddenSubscribedCalendars.call(context);
    expect(context.hiddenSubscribedCalendarIds).toEqual([77, 78]);

    Agenda.methods.changeHiddenSubscribedCalendars.call(context, ['79']);
    expect(context.hiddenSubscribedCalendarIds).toEqual([79]);
  });

  it('asks the server to leave out every hidden calendar of the user, subscribed ones included', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda/components/Agenda.vue'), 'utf8');

    expect(source).toContain("'attendees,conferences', this.hiddenOwnCalendarIds, sharedCalendarIds)");
    expect(source).toContain("this.$root.$on('agenda-subscribed-calendars-visibility-changed', this.changeHiddenSubscribedCalendars);");
  });

});
