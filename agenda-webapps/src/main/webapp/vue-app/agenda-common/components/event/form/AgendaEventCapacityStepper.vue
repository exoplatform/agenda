<template>
  <div class="d-flex flex-column">
    <div class="d-flex align-center">
      <span class="text-color subtitle-2 font-weight-bold me-auto">{{ $t('agenda.capacity.label') }}</span>
      <v-btn
        :disabled="!value"
        :title="$t('agenda.capacity.decrease')"
        icon
        small
        @click="decrease">
        <v-icon size="16" class="icon-default-color">fas fa-minus</v-icon>
      </v-btn>
      <!--
        The field takes the room of the text it shows, as plain text would,
        typed in or not (EXO-91878): an invisible copy of that text, in the
        same font, sizes the wrapper, which the field covers. The platform's
        skin gives every text input its own size, font, background and a shadow
        on focus, which the classes take back. Escape closes the drawer from the
        document, after the field: the field is left first, for the drawer to
        save the value typed.
      -->
      <span class="position-relative mx-2 text-no-wrap">
        <span class="invisible text-body" aria-hidden="true">{{ sizingText }}</span>
        <input
          :value="displayedValue"
          :aria-label="$t('agenda.capacity.label')"
          maxlength="9"
          inputmode="numeric"
          type="text"
          class="position-absolute l-0 t-0 full-width full-height text-body text-center transparent pa-0 ma-0 no-border no-box-shadow"
          @focus="startTyping"
          @keypress="acceptDigitsOnly"
          @keydown.enter.prevent="$event.target.blur()"
          @keydown.esc="$event.target.blur()"
          @input="keepDigits"
          @blur="applyTyped">
      </span>
      <v-btn
        :title="$t('agenda.capacity.increase')"
        icon
        small
        @click="increase">
        <v-icon size="16" class="icon-default-color">fas fa-plus</v-icon>
      </v-btn>
    </div>
    <div v-if="atMinimum" class="d-flex align-center caption text-sub-title">
      <v-icon size="14" class="info--text me-1">fas fa-info-circle</v-icon>
      <span>{{ $t('agenda.capacity.minimumHint') }}</span>
    </div>
  </div>
</template>

<script>
/**
 * The limit of an open event, 0 standing for no limit. It never goes below the
 * participants who said Yes: from no limit, the first value is their count,
 * and one step below it is no limit again. The server holds the same floor and
 * refuses a lower value, which this only spares the organiser.
 *
 * The value can also be typed, as a whole number (EXO-91878): it applies on
 * Enter or when the field is left, never while typing, so that typing 50 with
 * 7 Yes answers does not stop at the floor on its first digit. Empty or 0 is
 * no limit, and a value below the floor takes the floor, as the steps do.
 */
export default {
  props: {
    value: {
      type: Number,
      default: 0,
    },
    acceptedCount: {
      type: Number,
      default: 0,
    },
  },
  data: () => ({
    // What the field holds while it is being typed in, null otherwise
    typedValue: null,
  }),
  computed: {
    minimum() {
      return Math.max(this.acceptedCount, 1);
    },
    atMinimum() {
      return this.acceptedCount > 0 && this.value === this.acceptedCount;
    },
    displayedValue() {
      if (this.typedValue !== null) {
        return this.typedValue;
      }
      return this.value ? String(this.value) : this.$t('agenda.capacity.noLimit');
    },
    // An empty field is no limit, and keeps the room of the label
    sizingText() {
      return this.displayedValue || this.$t('agenda.capacity.noLimit');
    },
  },
  methods: {
    increase() {
      this.$emit('input', this.value ? this.value + 1 : this.minimum);
    },
    decrease() {
      this.$emit('input', this.value > this.minimum ? this.value - 1 : 0);
    },
    /**
     * "No limit" is not a number to edit: the field empties for the organiser
     * to type one, and gives the label back if they leave it empty.
     *
     * @param {Event} event the focus of the field
     * @returns {void}
     */
    startTyping(event) {
      this.typedValue = this.value ? String(this.value) : '';
      this.$nextTick(() => event.target.select());
    },
    acceptDigitsOnly(event) {
      if (event.key && event.key.length === 1 && !/\d/.test(event.key)) {
        event.preventDefault();
      }
    },
    /**
     * A paste is not filtered by the keys: what is not a digit is dropped from
     * the field itself, which the binding would not rewrite when the digits
     * left equal those it already holds.
     *
     * @param {Event} event the input of the field
     * @returns {void}
     */
    keepDigits(event) {
      const digits = event.target.value.replace(/\D/g, '');
      if (event.target.value !== digits) {
        event.target.value = digits;
      }
      this.typedValue = digits;
    },
    /**
     * Applied when the field is left, Enter included, rather than on its
     * change: a field left untouched, or edited back to what it held, fires
     * no change, and would keep its typing state — the steps then counting
     * from a value the field no longer shows.
     *
     * @param {Event} event the blur of the field
     * @returns {void}
     */
    applyTyped(event) {
      const typed = parseInt(event.target.value, 10) || 0;
      const capacity = typed && Math.max(typed, this.minimum);
      this.typedValue = null;
      if (capacity !== this.value) {
        this.$emit('input', capacity);
      }
    },
  },
};
</script>
