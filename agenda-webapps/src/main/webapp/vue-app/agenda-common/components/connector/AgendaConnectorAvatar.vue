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
<template>
  <v-avatar
    :class="showImage && 'white'"
    :size="size"
    tile>
    <v-icon
      v-if="displayIcon"
      :size="iconSize"
      :class="iconClass">
      {{ connector.icon }}
    </v-icon>
    <!-- no connector to draw means no image either: an empty src is not an
         empty image, it is a second request for the page itself -->
    <img
      v-else-if="showImage"
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
 *
 * The white tile belongs to the IMAGE, not to the avatar: a logo is drawn for
 * a light backdrop and needs one wherever it lands, while a font icon takes
 * the colour it is given and a white square behind it is just a white square.
 * Deciding it here rather than at each call site is what keeps a surface from
 * showing a blank tile the day an administrator swaps an image for a glyph.
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
    /*
     * The colour of the font icon, for the surfaces that do not draw it on the
     * card's own background: a row painted with its calendar's colour carries
     * its glyph in white like the rest of its text. An image is unaffected —
     * it brings its own colours.
     */
    iconClass: {
      type: String,
      default: 'icon-default-color',
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
     * Whether an image is what this avatar draws — and therefore whether the
     * white tile belongs on it. False for the font icon AND for the case where
     * there is nothing at all to draw, so a caller passing no connector gets an
     * empty square rather than a white one holding a broken image.
     *
     * @returns {Boolean} true when an image renders
     */
    showImage() {
      return !this.displayIcon && !!this.imageSrc;
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
