package com.cubetrek.subscription;

import com.cubetrek.database.SubscriptionRepository;
import com.cubetrek.database.Users;
import com.cubetrek.upload.garminconnect.GarminNewFileEventListener;
import com.google.gson.JsonSyntaxException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import com.stripe.net.Webhook;
import org.springframework.web.context.request.WebRequest;

@Controller
public class StripeSubscriptionController {

    // See https://stripe.com/docs/checkout/embedded/quickstart

    @Value("${stripe.api.key}")
    String StripeApiKey;

    @Value("${stripe.price}")
    String StripePrice;

    @Value("${cubetrek.address}")
    String domain;

    @Value("${stripe.endpoint.secret}")
    String endpointSecret;

    Logger logger = LoggerFactory.getLogger(StripeSubscriptionController.class);

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @ResponseBody
    @PostMapping(value = "/stripe-session", produces = "application/json")
    public Map<String, String> createCheckoutSession() throws StripeException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        Stripe.apiKey = StripeApiKey;
;
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setClientReferenceId("cubetrek"+user.getId())
                        .setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setReturnUrl(domain + "/subscribe-done")
                        .setCustomerEmail(user.getEmail())
                        .putMetadata("cubetrek_id", user.getId().toString())
                        .putMetadata("email", user.getEmail())
                        .setAutomaticTax(
                                SessionCreateParams.AutomaticTax.builder()
                                        .setEnabled(true)
                                        .build())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPrice(StripePrice)
                                        .build())

                        .build();

        Session session  = Session.create(params);

        Map<String, String> map = new HashMap<>();
        map.put("clientSecret", session.getRawJsonObject().getAsJsonPrimitive("client_secret").getAsString());
        return map;
    }

    //TODO: https://stripe.com/docs/payments/checkout/fulfill-orders
    @PostMapping(value="/stripe_hook")
    public ResponseEntity stripeHook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String stripeSignature) {

        Stripe.apiKey = StripeApiKey;

        Event stripeEvent = null;
        try {
            stripeEvent = Webhook.constructEvent(payload, stripeSignature, endpointSecret);
        } catch (JsonSyntaxException e) {
            logger.error("Stripe Subscription Webhook : JsonSyntaxException: payload: "+ payload, e);
            return ResponseEntity.badRequest().build();
        } catch (SignatureVerificationException e) {
            logger.error("Stripe Subscription Webhook : InvalidSignature: payload: "+ payload, e);
            return ResponseEntity.badRequest().build();
        }
        EventDataObjectDeserializer dataObjectDeserializer = stripeEvent.getDataObjectDeserializer();

        // Handle the event; see https://stripe.com/docs/api/events/types
        if (stripeEvent.getType().equals("checkout.session.completed")) {
            //System.out.println("Handled event type: " + stripeEvent.getType());

            if (dataObjectDeserializer.getObject().isPresent()) {
                Session sessionEvent = (Session) dataObjectDeserializer.getObject().get();
                String customerId = sessionEvent.getCustomer();
                String subscriptionId = sessionEvent.getSubscription();
                String cubetrekId = sessionEvent.getMetadata().get("cubetrek_id");

                //System.out.println("CustomerID "+ customerId +"; cubetrekId: "+cubetrekId + "; subscriptionId: "+subscriptionId);
                eventPublisher.publishEvent(new StripeNewEventListener.OnEvent(StripeNewEventListener.StripeEvent.checkout_session_completed, subscriptionId, customerId, cubetrekId));


            } else {
                logger.error("Stripe Subscription Webhook : Deserialization failed: payload: " + payload);
                return ResponseEntity.badRequest().build();
                // Deserialization failed, probably due to an API version mismatch.
                // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
                // instructions on how to handle this case, or return an error here.
            }
        } else if (stripeEvent.getType().equals("invoice.paid")) {
            //System.out.println("Handled event type: " + stripeEvent.getType());

            if (dataObjectDeserializer.getObject().isPresent()) {
                Invoice invoice = (Invoice) dataObjectDeserializer.getObject().get();

                String customerId = invoice.getCustomer();
                String subscriptionId = invoice.getSubscription();

                //System.out.println("CustomerID "+ customerId+ "; subscriptionId: "+subscriptionId);
                eventPublisher.publishEvent(new StripeNewEventListener.OnEvent(StripeNewEventListener.StripeEvent.invoice_paid, subscriptionId, customerId, null));
            } else {
                logger.error("Stripe Subscription Webhook : Deserialization failed: payload: " + payload);
                return ResponseEntity.badRequest().build();
                // Deserialization failed, probably due to an API version mismatch.
                // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
                // instructions on how to handle this case, or return an error here.
            }
        } else if (stripeEvent.getType().equals("invoice.payment_failed")) {
            if (dataObjectDeserializer.getObject().isPresent()) {
                Invoice invoice = (Invoice) dataObjectDeserializer.getObject().get();
                String customerId = invoice.getCustomer();
                String subscriptionId = invoice.getSubscription();
                eventPublisher.publishEvent(new StripeNewEventListener.OnEvent(StripeNewEventListener.StripeEvent.invoice_payment_failed, subscriptionId, customerId, null));
            }
        } else if (stripeEvent.getType().equals("customer.subscription.deleted")) {
            if (dataObjectDeserializer.getObject().isPresent()) {
                Subscription subscription = (Subscription) dataObjectDeserializer.getObject().get();
                String customerId = subscription.getCustomer();
                String subscriptionId = subscription.getId();
                eventPublisher.publishEvent(new StripeNewEventListener.OnEvent(StripeNewEventListener.StripeEvent.customer_subscription_deleted, subscriptionId, customerId, null));
            }
        } else if (stripeEvent.getType().equals("customer.subscription.updated")) {
            if (dataObjectDeserializer.getObject().isPresent()) {
                Subscription subscription = (Subscription) dataObjectDeserializer.getObject().get();
                String customerId = subscription.getCustomer();
                String subscriptionId = subscription.getId();
                eventPublisher.publishEvent(new StripeNewEventListener.OnEvent(StripeNewEventListener.StripeEvent.customer_subscription_updated, subscriptionId, customerId, null));
            }
        } else {
            logger.info("Stripe Subscription Webhook : Unhandled event type: " + stripeEvent.getType());
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscribe")
    public String showSubscription(WebRequest request, Model model) throws StripeException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        com.cubetrek.database.Subscription sub = subscriptionRepository.findByUser(user);

        if (sub != null && sub.isActive()) { //if user already has an active subscription send them to the portal

            Stripe.apiKey = StripeApiKey;

            com.stripe.param.billingportal.SessionCreateParams params = new com.stripe.param.billingportal.SessionCreateParams.Builder()
                    .setReturnUrl(domain + "/profile")
                    .setCustomer(sub.getStripeCustomerId())
                    .build();
            com.stripe.model.billingportal.Session portalSession = com.stripe.model.billingportal.Session.create(params);

            return "redirect:"+portalSession.getUrl();
        } else { //if no subscription exists, create new subscription
            model.addAttribute("user", user);
            return "subscribe";
        }
    }

    @GetMapping("/subscribe-done")
    public String showSubscription2(WebRequest request, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("user", user);

        return "subscribe2";
    }

}
