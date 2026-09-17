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

import AgendaEventsDetailsBody from '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue';

/**
 * The read view says Free, and says Private (EXO-90327 + EXO-90322): without
 * it, neither feature can be seen without opening the editor.
 *
 * <p><b>What this harness can and cannot say.</b> The details body pulls in the
 * attendee list, the connectors, the reminder drawer and the platform's own
 * date components, none of which are part of the agenda build, so mounting it
 * says nothing useful. The four computed properties that decide the row are
 * evaluated directly instead, and the template binding that consumes them is
 * pinned by reading the file — that the row lands where the eye expects it is
 * checked on a live page.</p>
 */
describe('The event details Show as / Visibility row', () => {

  const computed = AgendaEventsDetailsBody.computed;

  /**
   * Evaluates the row's computed properties over an event.
   *
   * @param {Object} event the event being read
   * @returns {Object} whether the row shows, and what it reads
   */
  function row(event) {
    const context = {event, $t: key => key};
    context.isFreeEvent = computed.isFreeEvent.call(context);
    context.eventVisibilityLabel = computed.eventVisibilityLabel.call(context);
    return {
      shown: computed.showAvailabilityVisibility.call(context),
      label: computed.availabilityVisibilityLabel.call(context),
    };
  }

  it.each([
    ['an event as every event was before the feature', {}],
    ['one explicitly busy', {availability: 'BUSY'}],
    ['one whose availability was never chosen', {availability: 'DEFAULT', visibility: 'DEFAULT'}],
  ])('stays out of the way for %s', (label, event) => {
    expect(row(event).shown).toBe(false);
  });

  it('says Free, so a free event can be told apart without opening the editor', () => {
    const shown = row({availability: 'FREE'});

    expect(shown.shown).toBe(true);
    expect(shown.label).toBe('agenda.availability.free');
  });

  it('says Private, and says the availability with it', () => {
    const shown = row({availability: 'FREE', visibility: 'PRIVATE'});

    expect(shown.shown).toBe(true);
    expect(shown.label).toBe('agenda.availability.free · agenda.visibility.private');
  });

  it('says a stated Public visibility too, on a busy event', () => {
    const shown = row({visibility: 'PUBLIC'});

    expect(shown.shown).toBe(true);
    expect(shown.label).toBe('agenda.availability.busy · agenda.visibility.public');
  });

  it('is the condition the template renders the row on', () => {
    const source = fs.readFileSync(path.join(__dirname,
                                             '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue'),
                                   'utf8');

    expect(source).toContain('v-if="showAvailabilityVisibility"');
    expect(source).toContain('{{ availabilityVisibilityLabel }}');
  });

});
