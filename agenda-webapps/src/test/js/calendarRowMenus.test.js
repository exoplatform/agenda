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
import Vue from 'vue';
import {mount, shallowMount} from '@vue/test-utils';

import AgendaPersonalCalendarList from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaPersonalCalendarList.vue';
import AgendaFilterCalendarItem from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue';
import {ROW_MENU_CLOSE_DELAY, ROW_MENU_CONTENT_CLASS} from '../../main/webapp/vue-app/agenda-common/js/CalendarRowMenuMixin.js';

/**
 * The calendar ⋮ menus of the left panel close on a press anywhere outside
 * them, and opening one closes any other (EXO-90252 follow-up #4).
 *
 * <p><b>What this harness can and cannot say.</b> Vuetify is not part of the
 * agenda build (the platform serves it), so the v-menu here is a stub with the
 * same model contract — a value prop and an input event. The pins are on the
 * state the menus are bound to and on the document listener that drives it,
 * which is where the defect lived: Vuetify's own click-outside listens on the
 * page's first [data-app] only. That Vuetify's menu follows the bound value, and
 * closes on Escape and on an entry chosen, is checked on a live page.</p>
 */
describe('Calendar row menus close on an outside press and when another opens', () => {

  Vue.config.ignoredElements = [/^v-(?!menu$)/];

  const VMenuStub = {
    name: 'VMenuStub',
    props: ['value', 'contentClass'],
    template: '<div class="v-menu-stub"><slot /></div>',
  };

  const PERSONAL = [{id: 10, name: 'Work'}, {id: 11, name: 'Home'}];

  let linkService;

  /**
   * Waits for every pending promise callback, with real timers.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Presses the mouse on an element, as a real press reaches the document.
   *
   * @param {Element} element the pressed element
   * @returns {void}
   */
  function press(element) {
    element.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
  }

  /**
   * Mounts the personal calendar list.
   *
   * @returns {Promise<Object>} the wrapper
   */
  async function mountList() {
    const wrapper = shallowMount(AgendaPersonalCalendarList, {
      attachTo: document.body,
      mocks: {
        $t: key => key,
        $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: PERSONAL.map(calendar => ({...calendar}))})},
        $calendarLinkService: linkService,
      },
      stubs: {'exo-confirm-dialog': true, 'v-menu': VMenuStub},
    });
    await flush();
    return wrapper;
  }

  /**
   * The menus of the list, by calendar label.
   *
   * @param {Object} wrapper the list
   * @param {String} label the calendar's label
   * @returns {Object} the menu stub wrapper
   */
  function menuOf(wrapper, label) {
    const row = wrapper.findAll('.agenda-calendar-settings').wrappers.find(one => one.find('v-checkbox').attributes('label') === label);
    return row.findComponent(VMenuStub);
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
    global.extensionRegistry = {loadExtensions: () => []};
  });

  beforeEach(() => {
    linkService = {getCalendarLinks: jest.fn().mockResolvedValue([])};
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('binds each row menu to one open state, with the content class a press inside is recognised by', async () => {
    const wrapper = await mountList();

    expect(menuOf(wrapper, 'Home').props('value')).toBe(false);
    expect(menuOf(wrapper, 'Home').props('contentClass')).toBe(ROW_MENU_CONTENT_CLASS);

    menuOf(wrapper, 'Home').vm.$emit('input', true);
    await Vue.nextTick();
    expect(menuOf(wrapper, 'Home').props('value')).toBe(true);

    menuOf(wrapper, 'Work').vm.$emit('input', true);
    await Vue.nextTick();
    expect(menuOf(wrapper, 'Work').props('value')).toBe(true);
    expect(menuOf(wrapper, 'Home').props('value')).toBe(false);

    // Escape and an entry chosen reach the state through the same input event.
    menuOf(wrapper, 'Work').vm.$emit('input', false);
    await Vue.nextTick();
    expect(menuOf(wrapper, 'Work').props('value')).toBe(false);
    wrapper.destroy();
  });

  it('closes an open menu on a press anywhere outside it, but not on a press inside its content', async () => {
    const wrapper = await mountList();
    menuOf(wrapper, 'Home').vm.$emit('input', true);
    await Vue.nextTick();
    jest.useFakeTimers();

    const content = document.createElement('div');
    content.className = ROW_MENU_CONTENT_CLASS;
    const entry = document.createElement('span');
    content.appendChild(entry);
    document.body.appendChild(content);
    press(entry);
    jest.advanceTimersByTime(ROW_MENU_CLOSE_DELAY + 50);
    await Vue.nextTick();
    expect(menuOf(wrapper, 'Home').props('value')).toBe(true);

    const grid = document.createElement('div');
    document.body.appendChild(grid);
    press(grid);
    jest.advanceTimersByTime(ROW_MENU_CLOSE_DELAY + 50);
    await Vue.nextTick();
    expect(menuOf(wrapper, 'Home').props('value')).toBe(false);

    content.remove();
    grid.remove();
    wrapper.destroy();
  });

  it('keeps the menu of another row open when the press that closed the first one opened it', async () => {
    const wrapper = await mountList();
    menuOf(wrapper, 'Home').vm.$emit('input', true);
    await Vue.nextTick();
    jest.useFakeTimers();

    press(document.body);
    menuOf(wrapper, 'Work').vm.$emit('input', true);
    jest.advanceTimersByTime(ROW_MENU_CLOSE_DELAY + 50);
    await Vue.nextTick();

    expect(menuOf(wrapper, 'Work').props('value')).toBe(true);
    expect(menuOf(wrapper, 'Home').props('value')).toBe(false);
    wrapper.destroy();
  });

  it('closes the personal menu when a space calendar menu opens, and the other way round', async () => {
    // Both lists in one application, as in the left panel: one $root carries
    // the event a menu sends when it opens.
    const host = mount({
      components: {AgendaPersonalCalendarList, AgendaFilterCalendarItem},
      data: () => ({
        space: {id: 20, owner: {id: 100, providerId: 'space', space: {displayName: 'Chemistry'}}, acl: {canEdit: true}},
      }),
      template: '<div><agenda-personal-calendar-list /><agenda-filter-calendar-item :calendar="space" :owner-ids="[100]" :selected-owner-ids="[]" /></div>',
    }, {
      attachTo: document.body,
      mocks: {
        $t: key => key,
        $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: PERSONAL.map(calendar => ({...calendar}))})},
        $calendarLinkService: linkService,
      },
      stubs: {'exo-confirm-dialog': true, 'v-menu': VMenuStub},
    });
    await flush();
    const list = host.findComponent(AgendaPersonalCalendarList);
    const item = host.findComponent(AgendaFilterCalendarItem);
    expect(item.vm.$root === list.vm.$root).toBe(true);
    const spaceMenu = () => item.findComponent(VMenuStub);

    menuOf(list, 'Home').vm.$emit('input', true);
    await Vue.nextTick();
    spaceMenu().vm.$emit('input', true);
    await Vue.nextTick();
    expect(spaceMenu().props('value')).toBe(true);
    expect(menuOf(list, 'Home').props('value')).toBe(false);

    menuOf(list, 'Work').vm.$emit('input', true);
    await Vue.nextTick();
    expect(spaceMenu().props('value')).toBe(false);
    host.destroy();
  });

  it('stops listening to the page once no menu is open', async () => {
    const wrapper = await mountList();
    const add = jest.spyOn(document, 'addEventListener');
    const remove = jest.spyOn(document, 'removeEventListener');

    menuOf(wrapper, 'Home').vm.$emit('input', true);
    await Vue.nextTick();
    menuOf(wrapper, 'Home').vm.$emit('input', false);
    await Vue.nextTick();

    expect(add).toHaveBeenCalledWith('mousedown', wrapper.vm.closeRowMenuOnOutsidePress);
    expect(remove).toHaveBeenCalledWith('mousedown', wrapper.vm.closeRowMenuOnOutsidePress);
    add.mockRestore();
    remove.mockRestore();
    wrapper.destroy();
  });

  it('drives the Shared with me row menu the same way', () => {
    const source = fs.readFileSync(path.resolve(__dirname,
      '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelRemoteCalendars.vue'), 'utf8');

    expect(source).toMatch(/import calendarRowMenuMixin from '..\/..\/js\/CalendarRowMenuMixin.js';/);
    expect(source).toMatch(/mixins: \[[^\]]*calendarRowMenuMixin[^\]]*\]/);
    expect(source).toMatch(/:value="isRowMenuOpen\(/);
    expect(source).toMatch(/content-class="agendaCalendarRowMenu"/);
    expect(source).toMatch(/@input="toggleRowMenu\(/);
  });

});
