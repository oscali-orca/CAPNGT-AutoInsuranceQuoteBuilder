/*context stores riskDetails, setRiskDetails, backendErrors, setBackendErrors*/
import {
    createContext,
    useContext,
    useState,
    type Dispatch,
    type ReactNode,
    type SetStateAction,
} from "react";

import type {RiskDetails} from "../types/quote";

//backend valildation errors assoicated with four inputs
//fields are optional bc backend may only reject a few fields
export type RiskErrors = Partial<Record<keyof RiskDetails, string>>;

interface QuoteDraftContextType {
    riskDetails: RiskDetails | null; //null means user didnt submit form yet
    setRiskDetails: (details: RiskDetails | null) => void; //either save validated details or clear draft

    //backend errors & function to update them for form
    backendErrors: RiskErrors;
    setBackendErrors: Dispatch<SetStateAction<RiskErrors>>;
}

/*createContext makes shared place where componenets access data (dont need to pass props down tree) */
const QuoteDraftContext = createContext<QuoteDraftContextType | undefined>(undefined);


export function QuoteDraftProvider({ /*makes risk details available to components wrapped inside it */
    children,
}: {
    children: ReactNode;
}) {
    //shared risk inputs
    const [riskDetails, setRiskDetails] = useState<RiskDetails | null>(null); /*useState stores risk details*/

    //shared backend validation errors
    const [backendErrors, setBackendErrors] = useState<RiskErrors>({});
    
    return (
        <QuoteDraftContext.Provider value={{ riskDetails, setRiskDetails, backendErrors, setBackendErrors,}}>
            {children}
        </QuoteDraftContext.Provider>
    );
}

//custom hook to access shared quote data (context)
export function useQuoteDraft() { 
    const context = useContext(QuoteDraftContext);
    if (!context) {
        throw new Error("useQuoteDraft must be used within a QuoteDraftProvider");
    }
    return context;
}