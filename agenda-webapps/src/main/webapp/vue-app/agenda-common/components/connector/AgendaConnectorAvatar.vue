<!--
Copyright (C) 2026 eXo Platform SAS.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <v-avatar
    :size="size"
    tile>
    <v-icon
      v-if="displayIcon"
      :size="iconSize"
      class="icon-default-color">
      {{ connector.icon }}
    </v-icon>
    <img
      v-else
      :src="imageSrc"
      :alt="altText">
  </v-avatar>
</template>
<script>
/**
 * The visual identity of a personal-calendar connector, resolved ONCE for
 * every surface that shows one (connect drawer, toolbar status badge,
 * timeline remote events, event form): the image the administrator uploaded
 * wins, else the font icon chosen in admin, else the connector's packaged
 * avatar. A hardcoded connector (Google, Office 365...) carries neither
 * `icon` nor `imageUrl` and keeps rendering its packaged avatar untouched.
 */
export default {
  props: {
    connector: {
      type: Object,
      default: null,
    },
    size: {
      type: [Number, String],
      default: 24,
    },
  },
  computed: {
    /**
     * Whether the identity is the admin-chosen font icon: only when one is
     * set AND no uploaded image overrides it — the same precedence the admin
     * screens apply, kept in one place so the surfaces cannot diverge.
     *
     * @returns {Boolean} true when the font icon is the identity to render
     */
    displayIcon() {
      return !!(this.connector && this.connector.icon && !this.connector.imageUrl);
    },
    /**
     * The image to render when the identity is not a font icon: the uploaded
     * image when one exists, else the packaged avatar the descriptor ships.
     *
     * @returns {String} the image URL, empty when no connector is given
     */
    imageSrc() {
      return this.connector && (this.connector.imageUrl || this.connector.avatar) || '';
    },
    /**
     * The font icon renders proportionally inside the avatar square.
     *
     * @returns {Number} the icon size in pixels
     */
    iconSize() {
      return Math.round(Number(this.size) * 0.75);
    },
    /**
     * Alternative text of the image, for accessibility.
     *
     * @returns {String} the connector's translated display name
     */
    altText() {
      return this.connector && this.connector.name && this.$t(this.connector.name) || '';
    },
  },
};
</script>
