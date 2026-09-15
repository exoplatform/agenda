/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

/**
 * How long a press outside a calendar menu waits before closing it — the delay
 * social's own menus use (SpaceHamburgerActionMenu, ActivityHeadMenu), so that a
 * press on another menu's activator settles before this one goes.
 */
export const ROW_MENU_CLOSE_DELAY = 200;

/**
 * The class of a calendar row menu's content, the part a press does not close.
 */
export const ROW_MENU_CONTENT_CLASS = 'agendaCalendarRowMenu';

/**
 * Closes the ⋮ menus of the agenda's calendar rows the way the platform's own
 * menus close: on any press outside them, and when another one opens.
 *
 * <p><b>Why Vuetify does not do it here.</b> The Vuetify the platform ships
 * (2.3.10, social's js/lib/vuetify.min.js) registers its click-outside listener
 * on the first [data-app] of the page — (document.querySelector("[data-app]")
 * || document.body).addEventListener("click", …, true). On an eXo page that
 * first [data-app] is #vuetify-apps, where the menu content is moved, while the
 * calendar rows and the grid live in #AgendaApplication, another [data-app]
 * that is not inside it: a click on the grid never reaches the listener, so the
 * menu stays open and several end up open at once.
 *
 * <p><b>The pattern followed.</b> Social's "workaround to fix closing menu when
 * clicking outside" in SpaceHamburgerActionMenu and ActivityHeadMenu: the menu
 * is driven by the component's state, a mousedown listener sits on the whole
 * document while a menu is open, and closes it after a short delay. Two
 * additions, both needed by lists of rows: a press inside the menu's own
 * content does not close it (so an entry held down a little longer still gets
 * its click), and opening a menu tells every other calendar menu of the app to
 * close (one root event, whichever list or row holds the other menu).
 *
 * <p>Escape and choosing an entry still close the menu through Vuetify's own
 * handling, which updates the state through the menu's input event.
 *
 * <p>Usage, on a v-menu of a row: :value="isRowMenuOpen(key)",
 * @input="toggleRowMenu(key, $event)" and content-class="agendaCalendarRowMenu".
 */
export default {
  data: () => ({
    openRowMenu: null,
  }),
  watch: {
    /**
     * Listens for presses anywhere on the page while a menu is open, and tells
     * the other calendar menus that this one opened.
     *
     * @param {String|Number} current the key of the open menu, null for none
     * @param {String|Number} previous the key of the menu open before
     * @returns {void}
     */
    openRowMenu(current, previous) {
      if (current === null) {
        document.removeEventListener('mousedown', this.closeRowMenuOnOutsidePress);
        return;
      }
      if (previous === null) {
        document.addEventListener('mousedown', this.closeRowMenuOnOutsidePress);
      }
      this.$root.$emit('agenda-calendar-row-menu-opened', this._uid);
    },
  },
  created() {
    this.$root.$on('agenda-calendar-row-menu-opened', this.closeRowMenuOpenedElsewhere);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-row-menu-opened', this.closeRowMenuOpenedElsewhere);
    document.removeEventListener('mousedown', this.closeRowMenuOnOutsidePress);
  },
  methods: {
    /**
     * Whether a row's menu is the open one.
     *
     * @param {String|Number} key the row's menu key
     * @returns {Boolean} true when open
     */
    isRowMenuOpen(key) {
      return this.openRowMenu !== null && this.openRowMenu === key;
    },
    /**
     * Follows a menu's own opening and closing: its activator, Escape, an entry
     * chosen.
     *
     * @param {String|Number} key the row's menu key
     * @param {Boolean} open whether the menu opened
     * @returns {void}
     */
    toggleRowMenu(key, open) {
      if (open) {
        this.openRowMenu = key;
      } else if (this.openRowMenu === key) {
        this.openRowMenu = null;
      }
    },
    /**
     * Closes the open menu after a press anywhere outside its content, once the
     * press has settled — unless a menu of another row opened meanwhile.
     *
     * @param {Event} event the mousedown
     * @returns {void}
     */
    closeRowMenuOnOutsidePress(event) {
      const target = event && event.target;
      if (target && target.closest && target.closest(`.${ROW_MENU_CONTENT_CLASS}`)) {
        return;
      }
      const key = this.openRowMenu;
      window.setTimeout(() => {
        if (this.openRowMenu === key) {
          this.openRowMenu = null;
        }
      }, ROW_MENU_CLOSE_DELAY);
    },
    /**
     * Closes this component's menu when a calendar menu opened in another one.
     *
     * @param {Number} openerUid the Vue identifier of the component that opened
     *          a menu
     * @returns {void}
     */
    closeRowMenuOpenedElsewhere(openerUid) {
      if (openerUid !== this._uid) {
        this.openRowMenu = null;
      }
    },
  },
};
