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

/**
 * The "Upcoming events" widget does not list a space's subscribed calendars
 * under its accepted-events filter (EXO-90373).
 *
 * <p>Off a space page the widget defaults to <code>allUsersSpaces</code> with
 * the <code>acceptedEvents</code> filter: it sends an attendee and the single
 * response ACCEPTED, and no owner at all. An absent ownerIds reaches the server
 * as null, which is the branch that expands to every space the viewer belongs
 * to — so without the opt-out the widget would list every feed of every one of
 * their spaces, in a list titled with what they personally accepted, and its
 * source and filter are portlet preferences a member cannot change.</p>
 *
 * <p>The request-building half is pinned on EventService, the decision half on
 * the widget's source, because mounting the widget would need the whole portlet
 * root it reads its settings from.</p>
 */
describe('The timeline widget and a space\'s subscribed calendars', () => {

  const widget = fs.readFileSync(path.resolve(__dirname,
                                              '../../main/webapp/vue-app/agenda-timeline/components/AgendaTimelineWidget.vue'),
                                 'utf8');

  it('asks to be spared them exactly when it lists accepted events', () => {
    expect(widget).toMatch(/const excludeSubscribedCalendars = agendaFilter === 'acceptedEvents';/);
  });

  it('passes that answer to the events request, in the argument the service reads', () => {
    const call = widget.slice(widget.indexOf('this.$eventService.getEvents('));
    const args = call.slice(call.indexOf('(') + 1, call.indexOf(')\n'));

    // The flag is the eleventh argument, after excludedCalendarIds and
    // calendarIds, which this widget does not use: passing it in the wrong
    // position would silently send a calendar list instead
    expect(args).toContain('\'attendees,conferences\', null, null, excludeSubscribedCalendars');
  });

  it('is the only thing the opt-out changes: no owner, attendee and response stay as they were', () => {
    expect(widget).toMatch(/const userIdentityId = agendaFilter === 'acceptedEvents' && eXo\.env\.portal\.userIdentityId \|\| null;/);
    expect(widget).toMatch(/const responseTypes = agendaFilter === 'acceptedEvents' \? \['ACCEPTED'\]/);
  });

});

/**
 * The request carries the flag only when it is asked for, so every caller that
 * does not pass it keeps the behaviour it had (EXO-90373).
 */
describe('EventService and the subscribed-calendar opt-out', () => {

  const service = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-common/js/EventService.js'),
                                  'utf8');

  it('names the flag last, so every existing call site is unchanged', () => {
    expect(service).toMatch(/export function getEvents\(query, ownerIds, attendeeIdentityId, start, end, limit, responseTypes, expand, excludedCalendarIds, calendarIds, excludeSubscribedCalendars\)/);
  });

  it('sends it only when true, absent being the server\'s own default', () => {
    expect(service).toMatch(/if \(excludeSubscribedCalendars\) \{\s*\n\s*params\.excludeSubscribedCalendars = true;\s*\n\s*\}/);
  });

});
