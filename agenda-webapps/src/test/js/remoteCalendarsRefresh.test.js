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

import AgendaLeftPanelRemoteCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelRemoteCalendars.vue';

/**
 * "Shared with me" no longer flickers when the connectors are asked again
 * (EXO-90355).
 *
 * <p>The section used to hide itself for the whole of every retrieval, and a
 * retrieval followed nearly every action in the agenda — each event saved or
 * answered, each push, each share granted or revoked — several times over per
 * action. The rows now stay on screen until the new answer replaces them, the
 * signals of one turn are gathered into one request, and a slower, older
 * answer never overwrites a newer one. A share that goes away still leaves
 * with the answer that no longer lists it.</p>
 *
 * <p><b>Harness.</b> The connector's listing is answered by hand, one deferred
 * per call, so a test can hold an answer back and look at the panel meanwhile.
 * Vuetify and the platform's avatar are not part of the agenda build, so their
 * tags are left as plain elements.</p>
 */
describe('"Shared with me" keeps its rows while it asks the connectors again', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar'];

  const CAMILLE = {
    id: '/dav/calendars/__uids__/751E6D1A-7FDB-49B2-B668-B569E9A5A42D/calendar:Default:9489C62D-0000-0000-0000-000000000000/',
    name: 'Camille',
    color: '#b8e986',
    readOnly: true,
    shared: true,
    ownerIdentityId: 5,
    ownerUsername: 'camille',
    ownerDisplayName: 'Camille Claudel',
    ownerKind: 'PERSON',
  };

  const ERIC = {
    id: '/dav/calendars/__uids__/4C60FEDD-0562-4903-A524-E95E1CCBCDE0/calendar:Default:4C60FEDD-0562-4903-A524-E95E1CCBCDE0/',
    name: 'Eric',
    color: '#4a90d9',
    readOnly: true,
    shared: true,
    ownerIdentityId: 7,
    ownerUsername: 'eric',
    ownerDisplayName: 'Eric Satie',
    ownerKind: 'PERSON',
  };

  /**
   * Waits for every pending promise callback and for the gathered retrieval's
   * timer, which is scheduled before this one.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * A connected CalDAV connector whose listing the test answers by hand: each
   * call to listCalendars appends its resolver to `answers`, in call order.
   *
   * @returns {Object} `{connector, answers}`
   */
  function deferredConnector() {
    const answers = [];
    return {
      answers,
      connector: {
        name: 'agenda.caldavCalendar',
        isCaldav: true,
        canListCalendars: true,
        connected: true,
        listCalendars: () => new Promise(resolve => answers.push(resolve)),
        hideCalendar: () => Promise.resolve(),
      },
    };
  }

  /**
   * Mounts the panel over the given connector and lets its first retrieval go
   * out, unanswered.
   *
   * @param {Object} connector the connector to list from
   * @returns {Promise<Object>} the wrapper
   */
  async function mountWith(connector) {
    const wrapper = shallowMount(AgendaLeftPanelRemoteCalendars, {
      propsData: {connectors: [connector]},
      mocks: {
        $t: (key, params) => (params ? `${key}|${params[0]}` : key),
        $remoteEventConnector: {excludeMirrorCalendar: (_connector, listed) => listed},
      },
    });
    await flush();
    return wrapper;
  }

  /**
   * Answers one listing and lets the panel draw it.
   *
   * @param {Object} wrapper the mounted panel
   * @param {Function} answer the resolver of the listing to answer
   * @param {Array} calendars what the connector lists
   * @returns {Promise} resolved once drawn
   */
  async function answerWith(wrapper, answer, calendars) {
    answer(calendars);
    await flush();
    await wrapper.vm.$nextTick();
  }

  /**
   * The names of the rows drawn, in order.
   *
   * @param {Object} wrapper the mounted panel
   * @returns {Array} the labels of the row checkboxes
   */
  function rowNames(wrapper) {
    return wrapper.findAll('v-checkbox').wrappers.map(checkbox => checkbox.attributes('label'));
  }

  it('draws nothing before the first answer, then keeps the rows on screen while a later retrieval is in flight', async () => {
    const {connector, answers} = deferredConnector();
    const wrapper = await mountWith(connector);
    expect(answers).toHaveLength(1);
    expect(wrapper.find('section').exists()).toBe(false);

    await answerWith(wrapper, answers[0], [CAMILLE]);
    expect(rowNames(wrapper)).toEqual(['Camille']);
    const section = wrapper.find('section').element;

    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    await wrapper.vm.$nextTick();
    expect(answers).toHaveLength(2);
    // Unanswered, and the row is still there, in the same section.
    expect(rowNames(wrapper)).toEqual(['Camille']);
    expect(wrapper.find('section').element).toBe(section);

    await answerWith(wrapper, answers[1], [CAMILLE, ERIC]);
    // The answer is patched into the section on screen, not into a new one.
    expect(rowNames(wrapper)).toEqual(['Camille', 'Eric']);
    expect(wrapper.find('section').element).toBe(section);
  });

  it('gathers the signals of one turn into one request', async () => {
    const {connector, answers} = deferredConnector();
    const wrapper = await mountWith(connector);
    await answerWith(wrapper, answers[0], [CAMILLE]);

    // What one drawer action fans out into: both root signals back to back,
    // and the connectors prop rebuilt as agenda-connectors-refresh rebuilds it
    // — a new array holding the same connector, which fires the watcher.
    wrapper.vm.$root.$emit('agenda-refresh-personal-calendars');
    wrapper.vm.$root.$emit('agenda-refresh');
    await wrapper.setProps({connectors: [connector]});
    await flush();

    expect(answers).toHaveLength(2);
  });

  it('keeps the newer answer when an older, slower one lands after it', async () => {
    const {connector, answers} = deferredConnector();
    const wrapper = await mountWith(connector);
    await answerWith(wrapper, answers[0], [CAMILLE]);

    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    expect(answers).toHaveLength(3);

    await answerWith(wrapper, answers[2], [ERIC]);
    expect(rowNames(wrapper)).toEqual(['Eric']);
    await answerWith(wrapper, answers[1], [CAMILLE, ERIC]);
    expect(rowNames(wrapper)).toEqual(['Eric']);
  });

  it('drops a share the new answer no longer lists, and the section with the last one', async () => {
    const {connector, answers} = deferredConnector();
    const wrapper = await mountWith(connector);
    await answerWith(wrapper, answers[0], [CAMILLE, ERIC]);
    expect(rowNames(wrapper)).toEqual(['Camille', 'Eric']);

    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    await answerWith(wrapper, answers[1], [ERIC]);
    expect(rowNames(wrapper)).toEqual(['Eric']);

    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    await answerWith(wrapper, answers[2], []);
    expect(wrapper.find('section').exists()).toBe(false);
  });
});
