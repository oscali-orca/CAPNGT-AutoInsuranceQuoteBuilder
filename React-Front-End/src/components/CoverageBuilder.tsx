import {Link} from "react-router-dom";
import {useQuoteDraft} from "../context/QuoteDraftContext";

export default function CoveragePlaceholder(){
    const { riskDetails } = useQuoteDraft(); //access the risk details from RiskDetailsForm

    if(!riskDetails){
        return <p>No risk details have been entered yet.</p>
    }

    return(
        <section style={{maxWidth:"500px", margin: "40px auto", padding: "24px",}}>
            <h3>Risk details received:</h3>
            <p>Driver age: {riskDetails.driverAge}</p>
            <p>Vehicle age: {riskDetails.vehicleAge}</p>
            <p>Annual mileage: {riskDetails.annualMileage}</p>
            <p>Prior claims: {riskDetails.priorClaims}</p>
            
            <Link to="/risk-details"> ← Edit Risk Details</Link>

        </section>

        
    );
}


