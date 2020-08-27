<template>
  <v-stepper v-model="stepper">
    <v-stepper-header>
      <v-stepper-step step="1">
        {{ $t('agenda.stepEventDetails') }}
      </v-stepper-step>
      <v-divider />
      <v-stepper-step step="2">
        {{ $t('agenda.stepEventChooseDate') }}
      </v-stepper-step>
      <v-btn
        color="grey"
        icon
        dark
        @click="closeDialog"
      >
        <v-icon>mdi-close</v-icon>
      </v-btn>
    </v-stepper-header>
    <v-stepper-items>
      <v-stepper-content step="1">
        <form ref="form1" @submit="nextStep">
          <v-container>
            <v-row>
              <label class="float-left mr-4">Create</label>
              <input
                ref="titleInput1"
                :placeholder="$t('agenda.eventTitle')"
                type="text"
                name="title"
                class="ignore-vuetify-classes my-3"
                required
              >
              <span>In</span>
              <input
                ref="selectInput1"
                type="text"
                name="name"
                class="ignore-vuetify-classes my-3"
                required
              >
            </v-row>
            <v-row>
              <v-col>
                <v-row>
                  <v-icon size="18" class="mr-11">
                    fas fa-map-marker-alt
                  </v-icon>
                  <input
                    ref="locationEvent"
                    :placeholder="$t('agenda.eventLocation')"
                    type="text"
                    name="locationEvent"
                    class="ignore-vuetify-classes my-3"
                    required
                  >
                </v-row>
                <v-row>
                  <v-icon size="18" class="mr-11">
                    fas fa-bell
                  </v-icon>
                  <label class="float-left mr-4"><div>{{ $t('agenda.label.notifyUsers') }}</div></label>
                  <input
                    ref="timeNotification"
                    type="text"
                    name="timeNotification"
                    class="ignore-vuetify-classes my-3"
                    required
                  >
                  <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
                    <option>Minutes</option>
                    <option>Hours</option>
                    <option>days</option>
                    <option>weeks</option>
                  </select>
                  <div>{{ $t('agenda.label.beforeStart') }}</div>
                </v-row>
                <v-row> <a>{{ $t('agenda.addNotification') }}</a></v-row>
                <v-row>
                  <v-icon size="18" class="mr-11">
                    fas fa-redo-alt
                  </v-icon>
                  <input
                    ref="repeatMode"
                    type="text"
                    name="repeatMode"
                    class="ignore-vuetify-classes my-3"
                    required
                  >
                </v-row>
                <v-row>
                  <v-icon size="18" class="mr-11">
                    fas fa-file-alt
                  </v-icon>
                  <textarea
                    ref="autoFocusInput1"
                    :placeholder="$t('agenda.description')"
                    type="text"
                    name="description"
                    rows="20"
                    maxlength="2000"
                    noresize
                    class="ignore-vuetify-classes my-3"
                  ></textarea>
                </v-row>
                <v-row>
                  <v-icon size="18" class="mr-2">
                    fas fa-paperclip
                  </v-icon>
                  <a>{{ $t('agenda.attachFile') }}</a>
                </v-row>
              </v-col>
              <v-col cols="1">
                <v-divider vertical />
              </v-col>
              <v-col>
                <v-row>
                  <v-icon size="18" class="mr-2">
                    fas fa-users
                  </v-icon>
                  <input
                    ref="addParticipants"
                    :placeholder="$t('agenda.addParticipants')"
                    type="text"
                    name="addParticipants"
                    class="ignore-vuetify-classes my-3"
                    required
                  >
                </v-row>
                <v-row>
                  <v-chip
                    color="primary"
                    close
                    pill
                  >
                    <v-avatar left>
                      <v-img src="https://cdn.vuetifyjs.com/images/john.png" />
                    </v-avatar>
                    New Tweets
                  </v-chip>
                </v-row>
              </v-col>
            </v-row>
          </v-container>
        </form>
        <v-row>
          <v-col />
          <v-col />
          <v-col>
            <v-btn text>
              Cancel
            </v-btn>
            <v-btn color="primary" @click="nextStep">
              Continue
            </v-btn>
          </v-col>
        </v-row>
      </v-stepper-content>
      <v-stepper-content step="2">
        <v-card
          class="mb-12"
          color="grey lighten-1"
          height="200px"
        />

        <v-btn
          color="primary"
          @click="nextStep"
        >
          Continue
        </v-btn>
        <v-btn text>
          Cancel
        </v-btn>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data () {
    return {
      stepper: 1
    };
  },
  methods:{
    closeDialog() {
      this.$emit('close');
    },
    nextStep() {
      if (this.stepper < 2) {
        this.stepper ++;
      } else {
        this.stepper = 1;
      }
    }
  }
};
</script>