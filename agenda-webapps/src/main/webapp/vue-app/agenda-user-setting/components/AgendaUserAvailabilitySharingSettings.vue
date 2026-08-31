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
          Short, and carrying the one thing a user could be surprised by: the
          events copied in from a calendar they connected for their own
          convenience count too. The setting is on by default, so the person
          reading this may never have opted into anything.
        -->
        <span>
          {{ $t('agenda.settings.shareAvailabilitySubTitle') }}
        </span>
        <!--
          Shown only when the switch is off, because that is the state whose
          consequence is not obvious: sharing nothing does not make you look
          free, it makes you unknown.
        -->
        <div v-if="!shared" class="text-subtitle mt-1">
          {{ $t('agenda.settings.shareAvailabilityOffHint') }}
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-switch
        v-model="shared"
        :loading="saving"
        :disabled="saving"
        class="mt-0 me-2"
        hide-details
        @change="saveSharing" />
    </v-list-item-action>
  </v-list-item>
</template>

<script>
/**
 * The two stored values. They are the tokens the server stores and the ones
 * the REST endpoint accepts, so the switch has no vocabulary of its own to
 * keep in step: on is "the people I share a space with", off is "nobody".
 */
const SHARED = 'shared-spaces';

const NOT_SHARED = 'nobody';

export default {
  data: () => ({
    // On, matching the server's own default, so the switch never shows a
    // state the server does not hold while the first read is in flight.
    shared: true,
    previouslyShared: true,
    saving: false,
  }),
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
     * Anything other than the off token reads as on, which is the default and
     * the state the server answers with when nothing was ever chosen.
     *
     * @returns {void}
     */
    retrieveSharing() {
      this.$settingsService.getAvailabilitySharing()
        .then(value => {
          this.shared = value !== NOT_SHARED;
          this.previouslyShared = this.shared;
        })
        .catch(error => {
          console.error('cannot read the availability sharing setting', error);
        });
    },
    /**
     * Stores the new choice, putting the switch back where it was when the
     * save fails, so it never shows a disclosure the server is not applying.
     *
     * @returns {void}
     */
    saveSharing() {
      this.saving = true;
      this.$settingsService.saveAvailabilitySharing(this.shared && SHARED || NOT_SHARED)
        .then(() => this.previouslyShared = this.shared)
        .catch(() => {
          this.shared = this.previouslyShared;
          this.$root.$emit('alert-message', this.$t('agenda.settings.shareAvailabilityError'), 'error');
        })
        .finally(() => this.saving = false);
    },
  },
};
</script>
