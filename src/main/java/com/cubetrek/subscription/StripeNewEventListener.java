package com.cubetrek.subscription;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.*;
import com.cubetrek.upload.StorageService;
import com.cubetrek.upload.UploadResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import lombok.Getter;
import lombok.Setter;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


@Component
public class StripeNewEventListener implements ApplicationListener<StripeNewEventListener.OnEvent> {

    Logger logger = LoggerFactory.getLogger(StripeNewEventListener.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Value("${stripe.api.key}")
    String StripeApiKey;

    @Async
    @Override
    public void onApplicationEvent(OnEvent event) {
        this.newStripeEvent(event);
    }

    public void newStripeEvent(OnEvent event) {
        if (event.stripeEvent == StripeEvent.checkout_session_completed) {
            Subscription sub = subscriptionRepository.findByStripeCustomerId(event.stripeCustomerId);

            if (sub == null) { //doesn't exist yet, create new
                sub = new Subscription();

                if (event.cubetrekId != null) {
                    try {
                        Long userid = Long.parseLong(event.cubetrekId);
                        if (!usersRepository.existsById(userid)) {
                            logger.error("Stripe Event: received invalid Cubetrek user id (non-existing) in checkout_session_completed event; User id: "+event.cubetrekId + "; Stripe customer id: "+event.stripeCustomerId + "; Stripe subscription id: "+event.stripeSubscriptionId);
                        } else {
                            //check if user already has a subscription
                            Subscription old_sub = subscriptionRepository.findByUser(usersRepository.getReferenceById(userid));
                            if (old_sub!=null) {
                                if (old_sub.isActive()) { //shoudl not happen...
                                    logger.error("Stripe Event: User id "+userid + " already has an active subscription!! Subscription id: "+old_sub.getStripeSubscriptionId()+ "; Stripe User Id: "+old_sub.getStripeCustomerId()+" --- WILL BE DELETED!!!! ----");
                                }
                                subscriptionRepository.delete(old_sub);
                            }

                            sub.setUser(usersRepository.getReferenceById(userid));
                            sub.setStripeSubscriptionId(event.stripeSubscriptionId);
                            sub.setStripeCustomerId(event.stripeCustomerId);
                            sub.setActive(true);
                            subscriptionRepository.save(sub);
                            logger.info("Stripe Event: Created New Subscription for user id: "+userid + "; Stripe customer id: "+event.stripeCustomerId + "; Stripe subscription id: "+event.stripeSubscriptionId);
                        }
                    } catch (NumberFormatException e) {
                        logger.error("Stripe Event: received invalid Cubetrek user id in checkout_session_completed event; User id: "+event.cubetrekId + "; Stripe customer id: "+event.stripeCustomerId + "; Stripe subscription id: "+event.stripeSubscriptionId);
                        return;
                    }
                } else {
                    logger.error("Stripe Event: received null Cubetrek user id in checkout_session_completed event; Stripe customer id: "+event.stripeCustomerId + "; Stripe subscription id: "+event.stripeSubscriptionId);
                    return;
                }
            } else { //sub already exists, update subscription id
                sub.setStripeSubscriptionId(event.stripeSubscriptionId);
                subscriptionRepository.save(sub);
                logger.info("Stripe Event: Updated Subscription for user id: "+sub.getUser().getId() + "; Stripe customer id: "+event.stripeCustomerId + "; Stripe subscription id: "+event.stripeSubscriptionId);
            }
            checkForUpdates(sub);
        } else if (event.stripeEvent == StripeEvent.invoice_paid) {
            Subscription sub = subscriptionRepository.findByStripeSubscriptionId(event.stripeSubscriptionId);
            if (sub == null) {
                logger.warn("Stripe Event: Received Invoice.Paid event for subscription id that does not exist (yet): "+event.stripeSubscriptionId+ "; ignore");
                return;
            }
            checkForUpdates(sub);
        } else if (event.stripeEvent == StripeEvent.invoice_payment_failed) {
            Subscription sub = subscriptionRepository.findByStripeSubscriptionId(event.stripeSubscriptionId);
            if (sub == null) {
                logger.warn("Stripe Event: Received Invoice.Paid event for subscription id that does not exist (yet): "+event.stripeSubscriptionId);
                return;
            }
            checkForUpdates(sub);
        } else if (event.stripeEvent == StripeEvent.customer_subscription_deleted) {
            Subscription sub = subscriptionRepository.findByStripeSubscriptionId(event.stripeSubscriptionId);
            if (sub == null) {
                logger.warn("Stripe Event: Received customer.subscription.deleted event for subscription id that does not exist (yet): "+event.stripeSubscriptionId);
                return;
            }
            checkForUpdates(sub);
        } else if (event.stripeEvent == StripeEvent.customer_subscription_updated) {
            Subscription sub = subscriptionRepository.findByStripeSubscriptionId(event.stripeSubscriptionId);
            if (sub == null) {
                logger.warn("Stripe Event: Received customer.subscription.updated event for subscription id that does not exist (yet): "+event.stripeSubscriptionId);
                return;
            }
            checkForUpdates(sub);
        }
    }

    @Transactional
    public void checkForUpdates(Subscription sub) {
        Stripe.apiKey = StripeApiKey;
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(sub.getStripeSubscriptionId());

            Users user = usersRepository.findById(sub.getUser().getId()).get();

            if (subscription.getStatus().equalsIgnoreCase("incomplete_expired") || subscription.getStatus().equalsIgnoreCase("canceled") || subscription.getStatus().equalsIgnoreCase("unpaid")) {
                sub.setActive(false);
                user.setUserTier(Users.UserTier.FREE);
                //usersRepository.updateUserTier(sub.getUser().getId(), Users.UserTier.FREE);
            } else {
                sub.setActive(true);
                user.setUserTier(Users.UserTier.PAID);
                //usersRepository.updateUserTier(sub.getUser().getId(), Users.UserTier.PAID);
            }

            usersRepository.save(user);

            sub.setCurrent_period_end(subscription.getCurrentPeriodEnd());
            sub.setCurrent_period_start(subscription.getCurrentPeriodStart());

            subscriptionRepository.save(sub);

            logger.info("Stripe Event: checked subscription id "+sub.getStripeSubscriptionId() + "; Status: "+subscription.getStatus() +"; set to "+(sub.isActive()?"Active":"Inactive"));

        } catch (StripeException e) {
            logger.error("Stripe event: error while trying to retrieve Stripe Subscription id "+sub.getStripeSubscriptionId(), e);
        }
    }

    public enum StripeEvent {
        checkout_session_completed,
        invoice_paid,
        invoice_payment_failed,
        customer_subscription_deleted,
        customer_subscription_updated
    }

    @Getter
    @Setter
    public static class OnEvent extends ApplicationEvent {
        StripeEvent stripeEvent;
        String stripeSubscriptionId;
        String stripeCustomerId;
        String cubetrekId;

        public OnEvent(StripeEvent stripeEvent, String stripeSubscriptionId, String stripeCustomerId, String cubetrekId) {
            super(stripeSubscriptionId);
            this.stripeSubscriptionId = stripeSubscriptionId;
            this.stripeEvent = stripeEvent;
            this.stripeCustomerId = stripeCustomerId;
            this.cubetrekId = cubetrekId;
        }
    }


}
