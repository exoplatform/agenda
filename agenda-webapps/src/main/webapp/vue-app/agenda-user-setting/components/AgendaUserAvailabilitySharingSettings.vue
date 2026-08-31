<template>
  <v-list-item>
    <v-list-item-content>
      <!-- text-color, like the other calendar rows on this page: text-header
           renders grey and lighter, which would make this row read as a
           different kind of setting than the ones above it. -->
      <v-list-item-title class="text-color">
        {{ $t('agenda.settings.shareAvailability') }}
      </v-list-item-title>
      <v-list-item-subtitle>
        <!--
          The whole point of this line. The choice is on by default, so the
          person reading it may never have opted into anything: it has to say
          what is shared (times only), what counts (the events copied in from
          a calendar they connected for their own convenience) and what is
          never shared (anything about the events themselves). Buried, this
          setting would be a disclosure nobody was told about.
        -->
        <span>
          {{ $t('agenda.settings.shareAvailabilitySubTitle') }}
        </span>
        <!--
          Shown only on "Nobody", because that is the choice whose consequence
          is not obvious: sharing nothing does not make you look free, it makes
          you unknown, and a person deciding to hide their calendar deserves to
          know that it will not silently get them booked over.
        -->
        <div v-if="sharing === 'nobody'" class="text-subtitle mt-1">
          {{ $t('agenda.settings.shareAvailabilityNobodyHint') }}
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-select
        v-model="sharing"
        :items="options"
        :loading="saving"
        :disabled="saving"
        class="mt-0 me-2 pt-0"
        item-value="value"
        item-text="text"
        dense
        hide-details
        @change="saveSharing" />
    </v-list-item-action>
  </v-list-item>
</template>

<script>
/**
 * The stored values, in the order they are offered — widest first, so the
 * list reads from "most people" down to "nobody". They are the same tokens
 * the server stores and the same ones the REST endpoint accepts; there is no
 * second vocabulary to keep in step.
 */
const SHARING_VALUES = ['everyone', 'shared-spaces', 'nobody'];

/**
 * What is applied when the server has never been asked, and when a save
 * fails before anything was read. It matches the server's own default, so the
 * control never shows a state the server does not hold.
 */
const DEFAULT_SHARING = 'shared-spaces';

export default {
  data: () => ({
    sharing: DEFAULT_SHARING,
    previousSharing: DEFAULT_SHARING,
    saving: false,
  }),
  computed: {
    /**
     * The three choices, labelled.
     *
     * @returns {Array} the items the select offers
     */
    options() {
      return [
        {value: 'everyone', text: this.$t('agenda.settings.shareAvailabilityOptionEveryone')},
        {value: 'shared-spaces', text: this.$t('agenda.settings.shareAvailabilityOptionSharedSpaces')},
        {value: 'nobody', text: this.$t('agenda.settings.shareAvailabilityOptionNobody')},
      ];
    },
  },
  created() {
    this.retrieveSharing();
  },
  methods: {
    /**
     * Reads the stored choice from the server.
     *
     * Read on its own rather than taken from the settings object this page
     * already holds: the choice is stored under its own key, precisely so that
     * a settings save that does not carry it cannot reset it. Reading it out
     * of that payload would put it back into the shape the storage avoids.
     *
     * @returns {void}
     */
    retrieveSharing() {
      this.$settingsService.getAvailabilitySharing()
        .then(value => {
          this.sharing = SHARING_VALUES.includes(value) && value || DEFAULT_SHARING;
          this.previousSharing = this.sharing;
        })
        .catch(error => {
          console.error('cannot read the availability sharing setting', error);
        });
    },
    /**
     * Stores the new choice, putting the control back where it was when the
     * save fails, so it never shows a disclosure the server is not applying.
     *
     * @returns {void}
     */
    saveSharing() {
      this.saving = true;
      this.$settingsService.saveAvailabilitySharing(this.sharing)
        .then(() => this.previousSharing = this.sharing)
        .catch(() => {
          this.sharing = this.previousSharing;
          this.$root.$emit('alert-message', this.$t('agenda.settings.shareAvailabilityError'), 'error');
        })
        .finally(() => this.saving = false);
    },
  },
};
</script>
