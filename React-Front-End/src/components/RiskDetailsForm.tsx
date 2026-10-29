import {useState, useEffect, type FormEvent} from "react";
import {useQuoteDraft} from "../context/QuoteDraftContext";
import type {RiskDetails} from "../types/quote";
import {useNavigate} from "react-router-dom";
import {getCurrentSession, getQuoteConfig} from "../services/quoteApi";
import "./RiskDetailsForm.css";
import CoverageTabs from "./CoverageTabs";
import "./CoverageBuilder.css";

type RiskField = keyof RiskDetails; //4 fields that are customer's risk details, keyof makes ts type w/property names of interface driverAge|vehicleAge\annualMileage|priorClaims
type FormValues = Record<RiskField, string>; //Record makes object type w/ <property key, porperty val> 
type FormErrors = Partial<Record<RiskField, string>>; //partial makes error messages optional

//validation rules & hints displayed to user for entering input
const fieldRules: Record<
    RiskField, 
    {label: string; min: number; max: number; hint: string} 
> = {
    driverAge: {
        label: "Driver Age",
        min: 18,
        max: 80,
        hint: "18 to 80 years"
    },
    vehicleAge: {
        label: "Vehicle Age",
        min: 0,
        max: 20,
        hint: "0 to 20 years"
    },
    annualMileage: {
        label: "Annual mileage",
        min: 0,
        max: 50000,
        hint: "0 to 50,000 miles"
    },
    priorClaims: {
        label: "Claims in the past 3 years",
        min: 0,
        max: 3,
        hint: "0 to 3 prior claims"
    },
};

//fields to render in form in order
const fields: RiskField[] = [
    "driverAge",
    "vehicleAge",
    "annualMileage",
    "priorClaims"
];

//validates 1 field and returns error message if invalid
//returns undefined when value is valid
function validateField(
    field: RiskField,
    value: string
): string | undefined {
    const rule = fieldRules[field];

    //rejects missing values
    if (value.trim() === "") {
        return `${rule.label} is required`;
    }

    //only allows whole numbers
    if(!/^\d+$/.test(value)) {
        return `${rule.label} must be a whole non-negative number`;
    }

    const number = Number(value);

    //checks that input is within range
    if(
        !Number.isSafeInteger(number) ||
        number < rule.min ||
        number > rule.max
    ) {
        return `${rule.label} must be between ${rule.min} and ${rule.max}`;
    }
    return undefined;
}

export default function RiskDetailsForm() {
    const {riskDetails, setRiskDetails, backendErrors, setBackendErrors,} = useQuoteDraft(); //shared quote draft, which the other screens can access through context

    const navigate = useNavigate(); //react router hook to move to next screen

    //tracks what user is typing, intialize from context so shared values appear when return to page
    //starting blank for a new quote, restore saved vals when editing
    const [values, setValues] = useState<FormValues>({
        driverAge: riskDetails ? String(riskDetails.driverAge) : "",
        vehicleAge: riskDetails ? String(riskDetails.vehicleAge) : "",
        annualMileage: riskDetails ? String(riskDetails.annualMileage) : "",
        priorClaims: riskDetails ? String(riskDetails.priorClaims) : "",
    });

    const [errors, setErrors] = useState<FormErrors>({}); //stores field specific validation messages

    //state error info from backend
    const[apiError, setApiError] = useState("");
    const [customerName, setCustomerName] = useState("");

    //load signed in customer and their active quote information
    useEffect(() => {
        async function loadInitalData() {
            try{
                const session = await getCurrentSession();
                setCustomerName(session.display_name);

                const quoteConfig = await getQuoteConfig();
                console.log("Active quote config:", quoteConfig);
            } catch (error) {
                setApiError(error instanceof Error? error.message : "Could not load quote information");
            }
        }
        loadInitalData();
    }, []);

    //runs whenever user edits input
    function handleChange(field: RiskField, value: string) {
        setValues((previous) => ({ //updates only field that changed
            ...previous,
            [field]: value,
        }));

        //clears error for the field that changed
        //field going to be validates again on submit
        setErrors((previous) => ({ 
            ...previous,
            [field]: undefined,
        }));

        //clear backenderror when user fixes input
        setBackendErrors((previous) => ({
            ...previous,
            [field]: undefined,
        }));
    }

    //runs when user clicks continue to coverage
    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const nextErrors: FormErrors = {};

        //validate all four fields
        for (const field of fields) {
            const error = validateField(field, values[field]);

            if (error) {
                nextErrors[field] = error;
            }
        }

        setErrors(nextErrors); //updated displayed error messages

        //stop submission if any field invalid
        if (Object.keys(nextErrors).length > 0) {
            return;
        }

        //convert validated str -> nums for backend
        const validatedDetails: RiskDetails = {
            driverAge: Number(values.driverAge),
            vehicleAge: Number(values.vehicleAge),
            annualMileage: Number(values.annualMileage),
            priorClaims: Number(values.priorClaims),
        };

        setBackendErrors({}); //clear any previous backend errors before submitting
        setRiskDetails(validatedDetails); //store valid inputs in shared context to use later
        navigate("/coverage"); //move to coverage builder screen after submit

    console.log("Validated risk details:", validatedDetails); //Coverage builder navigation
  }

    
return (
    <section className="risk-details-page">
        <CoverageTabs />
        
        <h1 className="risk-details-title">
            Vehicle &amp; Driver Details
        </h1>

        <p className="risk-details-subtitle">
            Step 1: Enter the details for your training estimate.
        </p>

        {/*show signed in customer */}
        {customerName && (
            <p className="risk-details-customer">
                Customer: {customerName}
            </p>
        )}

        {/*show errors if API request fails*/}
        {apiError && (
            <p role="alert" className="risk-details-api-error">
                {apiError}
            </p>
        )}

        {/*noValidate lets us use custom field messages*/}
        <form
            onSubmit={handleSubmit}
            noValidate
            className="risk-details-form"
        >
            {/*display four fields in a two-column grid*/}
            <div className="risk-details-grid">
                {fields.map(field => {
                    const rule = fieldRules[field];

                    //use frontend error first, then show backend error
                    const errorMessage = errors[field] ?? backendErrors[field];

                    return (
                        <div key={field} className="risk-field">
                            <label htmlFor={field}>
                                {rule.label}
                            </label>

                            {/*controlled input - value comes from react state*/}
                            <input
                                id={field}
                                name={field}
                                type="text"
                                inputMode="numeric"
                                value={values[field]}
                                onChange={(event) =>
                                    handleChange(field, event.target.value)
                                }
                                aria-invalid={Boolean(errorMessage)}
                                aria-describedby={`${field}-message`}
                            />

                            {/*display error message or hint below the input*/}
                            <small id={`${field}-message`}>
                                {errorMessage ? (
                                    <span
                                        className="risk-field-error"
                                        role="alert"
                                    >
                                        {errorMessage}
                                    </span>
                                ) : (
                                    rule.hint
                                )}
                            </small>
                        </div>
                    );
                })}
            </div>

            {/*submit validated risk details and continue to coverage builder*/}
            <button
                type="submit"
                className="risk-details-button"
            >
                Continue to Coverage
            </button>
        </form>
    </section>
);
}


